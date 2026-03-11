package com.travelapi.service;

import com.travelapi.dto.Dtos.*;
import com.travelapi.exception.ApiException;
import com.travelapi.model.*;
import com.travelapi.repository.Repositories;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for trip management.
 *
 * Integrates with:
 *  - PostgreSQL (Cloud SQL) via JPA for persistence
 *  - GCP Cloud Pub/Sub via PubSubService for async events (trip shared, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final Repositories.TripRepo tripRepository;
    private final Repositories.UserRepo userRepository;
    private final PubSubService pubSubService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public TripResponse createTrip(TripRequest request, String userEmail) {
        User user = findUser(userEmail);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ApiException("End date cannot be before start date", HttpStatus.BAD_REQUEST);
        }

        Trip trip = Trip.builder()
                .user(user)
                .title(request.getTitle())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .status(request.getStatus() != null ? request.getStatus() : Trip.TripStatus.PLANNED)
                .build();

        trip = tripRepository.save(trip);
        log.info("Trip created: id={} user={}", trip.getId(), userEmail);

        // Publish event to Pub/Sub — other microservices (e.g. notification service) can subscribe
        pubSubService.publishTripCreated(trip.getId(), user.getId(), trip.getTitle());

        return toResponse(trip);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Page<TripResponse> getTrips(String userEmail, int page, int size, String sortBy) {
        User user = findUser(userEmail);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        return tripRepository.findByUserId(user.getId(), pageable).map(this::toResponse);
    }

    public TripResponse getTripById(Long tripId, String userEmail) {
        Trip trip = findTripForUser(tripId, userEmail);
        return toResponseWithDetails(trip);
    }

    public List<TripResponse> searchByDestination(String destination, String userEmail) {
        User user = findUser(userEmail);
        return tripRepository.searchByDestination(user.getId(), destination)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public TripResponse updateTrip(Long tripId, TripRequest request, String userEmail) {
        Trip trip = findTripForUser(tripId, userEmail);

        trip.setTitle(request.getTitle());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNotes(request.getNotes());
        if (request.getStatus() != null) trip.setStatus(request.getStatus());

        trip = tripRepository.save(trip);
        log.info("Trip updated: id={}", tripId);
        return toResponse(trip);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTrip(Long tripId, String userEmail) {
        Trip trip = findTripForUser(tripId, userEmail);
        tripRepository.delete(trip);
        log.info("Trip deleted: id={}", tripId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    private Trip findTripForUser(Long tripId, String userEmail) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException("Trip not found", HttpStatus.NOT_FOUND));
        if (!trip.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        }
        return trip;
    }

    public TripResponse toResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .notes(trip.getNotes())
                .status(trip.getStatus().name())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    private TripResponse toResponseWithDetails(Trip trip) {
        List<ReminderResponse> reminders = trip.getReminders() == null ? List.of() :
                trip.getReminders().stream().map(r -> ReminderResponse.builder()
                        .id(r.getId()).tripId(trip.getId()).title(r.getTitle())
                        .remindAt(r.getRemindAt()).sent(r.getSent()).createdAt(r.getCreatedAt())
                        .build()).collect(Collectors.toList());

        List<AttachmentResponse> attachments = trip.getAttachments() == null ? List.of() :
                trip.getAttachments().stream().map(a -> AttachmentResponse.builder()
                        .id(a.getId()).tripId(trip.getId()).fileName(a.getFileName())
                        .contentType(a.getContentType()).sizeBytes(a.getSizeBytes())
                        .uploadedAt(a.getUploadedAt())
                        .build()).collect(Collectors.toList());

        return TripResponse.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .notes(trip.getNotes())
                .status(trip.getStatus().name())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .reminders(reminders)
                .attachments(attachments)
                .build();
    }
}
