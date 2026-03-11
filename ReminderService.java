package com.travelapi.service;

import com.travelapi.dto.Dtos.*;
import com.travelapi.exception.ApiException;
import com.travelapi.model.*;
import com.travelapi.repository.Repositories;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages trip reminders.
 *
 * Scheduling strategy:
 *  - Spring @Scheduled polls every minute for due reminders (works on Cloud Run with min instances >= 1)
 *  - GCP Cloud Scheduler is configured (via Terraform) to call POST /api/internal/reminders/process
 *    as an alternative trigger — ensuring reminders fire even if the pod was cold.
 *  - On trigger, due reminders are published to Cloud Pub/Sub (reminder-created topic).
 *  - A downstream subscriber (or the same service) processes notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderService {

    private final Repositories.ReminderRepo reminderRepository;
    private final Repositories.TripRepo tripRepository;
    private final Repositories.UserRepo userRepository;
    private final PubSubService pubSubService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ReminderResponse createReminder(ReminderRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ApiException("Trip not found", HttpStatus.NOT_FOUND));

        if (!trip.getUser().getId().equals(user.getId())) {
            throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        }

        Reminder reminder = Reminder.builder()
                .trip(trip)
                .user(user)
                .title(request.getTitle())
                .remindAt(request.getRemindAt())
                .build();

        reminder = reminderRepository.save(reminder);

        // Publish to Pub/Sub so downstream services know a reminder was created
        pubSubService.publishReminderCreated(reminder.getId(), user.getId(),
                reminder.getTitle(), reminder.getRemindAt());

        log.info("Reminder created: id={} tripId={}", reminder.getId(), trip.getId());
        return toResponse(reminder);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<ReminderResponse> getRemindersForTrip(Long tripId, String userEmail) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException("Trip not found", HttpStatus.NOT_FOUND));
        if (!trip.getUser().getEmail().equals(userEmail)) {
            throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        }
        return reminderRepository.findByTripId(tripId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    /**
     * Runs every 60 seconds. Fetches all unsent reminders whose remindAt has passed,
     * publishes them to Pub/Sub, then bulk-marks them as sent.
     *
     * This is also callable via the internal HTTP endpoint (triggered by Cloud Scheduler).
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processReminders() {
        List<Reminder> dueReminders = reminderRepository.findUnsentRemindersBeforeOrAt(LocalDateTime.now());

        if (dueReminders.isEmpty()) return;

        log.info("Processing {} due reminders", dueReminders.size());

        for (Reminder r : dueReminders) {
            try {
                pubSubService.publishReminderDue(r.getId(), r.getUser().getId(), r.getTitle(), r.getRemindAt());
            } catch (Exception e) {
                log.error("Failed to publish reminder id={}: {}", r.getId(), e.getMessage());
            }
        }

        // Bulk update: mark all as sent in one query
        List<Long> ids = dueReminders.stream().map(Reminder::getId).collect(Collectors.toList());
        int updated = reminderRepository.markAsSent(ids);
        log.info("Marked {} reminders as sent", updated);
    }

    private ReminderResponse toResponse(Reminder r) {
        return ReminderResponse.builder()
                .id(r.getId())
                .tripId(r.getTrip().getId())
                .title(r.getTitle())
                .remindAt(r.getRemindAt())
                .sent(r.getSent())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
