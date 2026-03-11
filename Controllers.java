package com.travelapi.controller;

import com.travelapi.dto.Dtos.*;
import com.travelapi.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

// ─── Reminder Controller ──────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
class ReminderController {

    private final ReminderService reminderService;

    /** POST /api/reminders — Create a reminder for a trip */
    @PostMapping
    public ResponseEntity<ApiResponse<ReminderResponse>> createReminder(
            @Valid @RequestBody ReminderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ReminderResponse reminder = reminderService.createReminder(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reminder, "Reminder created"));
    }

    /** GET /api/reminders?tripId=1 — List reminders for a trip */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getReminders(
            @RequestParam Long tripId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ReminderResponse> reminders = reminderService.getRemindersForTrip(tripId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(reminders));
    }
}

// ─── Attachment Controller ────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/trips/{tripId}/attachments")
@RequiredArgsConstructor
class AttachmentController {

    private final StorageService storageService;

    /**
     * POST /api/trips/{tripId}/attachments
     * Upload a file to GCP Cloud Storage and persist metadata.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(
            @PathVariable Long tripId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        AttachmentResponse attachment = storageService.uploadAttachment(tripId, file, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(attachment, "File uploaded to Cloud Storage"));
    }

    /** GET /api/trips/{tripId}/attachments — List attachments with signed download URLs */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> list(
            @PathVariable Long tripId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<AttachmentResponse> attachments = storageService.getAttachments(tripId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(attachments));
    }

    /** DELETE /api/trips/{tripId}/attachments/{attachmentId} */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long tripId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        storageService.deleteAttachment(attachmentId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null, "Attachment deleted"));
    }
}

// ─── Analytics Controller ─────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final com.travelapi.repository.Repositories.UserRepo userRepository;

    /**
     * GET /api/analytics/top-destinations?limit=5
     * Returns top N destinations queried from GCP BigQuery.
     */
    @GetMapping("/top-destinations")
    public ResponseEntity<ApiResponse<List<TripAnalyticsResponse>>> topDestinations(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
        List<TripAnalyticsResponse> result = analyticsService.getTopDestinations(user.getId(), limit);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/analytics/monthly
     * Monthly trip stats from BigQuery.
     */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<TripAnalyticsResponse>>> monthly(
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
        List<TripAnalyticsResponse> result = analyticsService.getMonthlyTripStats(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

// ─── Internal Controller (called by GCP Cloud Scheduler) ─────────────────────

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
class InternalController {

    private final ReminderService reminderService;

    /**
     * POST /api/internal/reminders/process
     * Triggered by GCP Cloud Scheduler to process due reminders.
     * In production, secure this endpoint with a Cloud Scheduler OIDC token.
     */
    @PostMapping("/reminders/process")
    public ResponseEntity<ApiResponse<String>> processReminders() {
        reminderService.processReminders();
        return ResponseEntity.ok(ApiResponse.ok("OK", "Reminder processing triggered"));
    }
}
