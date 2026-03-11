package com.travelapi.service;

import com.google.cloud.storage.*;
import com.travelapi.dto.Dtos.AttachmentResponse;
import com.travelapi.exception.ApiException;
import com.travelapi.model.*;
import com.travelapi.repository.Repositories;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages trip attachment uploads/downloads via GCP Cloud Storage.
 *
 * GCS bucket is provisioned by Terraform with:
 *  - Uniform bucket-level access (no ACLs)
 *  - Service Account travelapi@PROJECT.iam.gserviceaccount.com with roles/storage.objectAdmin
 *  - Lifecycle rule: delete objects older than 365 days
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorageService {

    private final Storage googleCloudStorage;
    private final Repositories.AttachmentRepo attachmentRepository;
    private final Repositories.TripRepo tripRepository;
    private final Repositories.UserRepo userRepository;

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public AttachmentResponse uploadAttachment(Long tripId, MultipartFile file, String userEmail) throws IOException {
        Trip trip = findTripForUser(tripId, userEmail);

        String gcsPath = buildGcsPath(tripId, file.getOriginalFilename());

        BlobId blobId = BlobId.of(bucketName, gcsPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        googleCloudStorage.create(blobInfo, file.getBytes());
        log.info("Uploaded file to GCS: bucket={} path={}", bucketName, gcsPath);

        Attachment attachment = Attachment.builder()
                .trip(trip)
                .fileName(file.getOriginalFilename())
                .gcsPath(gcsPath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build();

        attachment = attachmentRepository.save(attachment);

        return AttachmentResponse.builder()
                .id(attachment.getId())
                .tripId(tripId)
                .fileName(attachment.getFileName())
                .downloadUrl(generateSignedUrl(gcsPath))
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }

    // ── List attachments ──────────────────────────────────────────────────────

    public List<AttachmentResponse> getAttachments(Long tripId, String userEmail) {
        findTripForUser(tripId, userEmail);  // verify ownership
        return attachmentRepository.findByTripId(tripId).stream()
                .map(a -> AttachmentResponse.builder()
                        .id(a.getId())
                        .tripId(tripId)
                        .fileName(a.getFileName())
                        .downloadUrl(generateSignedUrl(a.getGcsPath()))
                        .contentType(a.getContentType())
                        .sizeBytes(a.getSizeBytes())
                        .uploadedAt(a.getUploadedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteAttachment(Long attachmentId, String userEmail) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ApiException("Attachment not found", HttpStatus.NOT_FOUND));

        if (!attachment.getTrip().getUser().getEmail().equals(userEmail)) {
            throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        }

        googleCloudStorage.delete(BlobId.of(bucketName, attachment.getGcsPath()));
        attachmentRepository.delete(attachment);
        log.info("Deleted attachment id={} from GCS path={}", attachmentId, attachment.getGcsPath());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates a signed URL valid for 15 minutes — allows clients to download
     * directly from GCS without routing through the API server.
     */
    private String generateSignedUrl(String gcsPath) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, gcsPath)).build();
            URL signedUrl = googleCloudStorage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature());
            return signedUrl.toString();
        } catch (Exception e) {
            log.warn("Could not generate signed URL for {}: {}", gcsPath, e.getMessage());
            return null;
        }
    }

    private String buildGcsPath(Long tripId, String originalFilename) {
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        return String.format("trips/%d/%s%s", tripId, UUID.randomUUID(), ext);
    }

    private Trip findTripForUser(Long tripId, String userEmail) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException("Trip not found", HttpStatus.NOT_FOUND));
        if (!trip.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        }
        return trip;
    }
}
