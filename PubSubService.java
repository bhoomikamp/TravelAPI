package com.travelapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GCP Cloud Pub/Sub integration.
 *
 * Topics (provisioned via Terraform):
 *  - reminder-created   : fired when a new reminder is saved
 *  - reminder-due       : fired by the scheduler when a reminder's time has arrived
 *  - trip-shared        : fired when a trip is shared with another user
 *
 * Subscriptions (provisioned via Terraform):
 *  - reminder-processor-sub    → pulls from reminder-due
 *  - trip-shared-processor-sub → pulls from trip-shared
 *
 * In a full microservice setup each subscription would be pulled by a separate service.
 * Here the same application demonstrates the publisher/subscriber pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PubSubService {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gcp.pubsub.topics.reminder-created}")
    private String reminderCreatedTopic;

    @Value("${gcp.pubsub.topics.trip-shared}")
    private String tripSharedTopic;

    // ── Publishers ────────────────────────────────────────────────────────────

    public void publishTripCreated(Long tripId, Long userId, String title) {
        publish(tripSharedTopic, Map.of(
                "event", "TRIP_CREATED",
                "tripId", tripId,
                "userId", userId,
                "title", title
        ));
    }

    public void publishReminderCreated(Long reminderId, Long userId, String title, LocalDateTime remindAt) {
        publish(reminderCreatedTopic, Map.of(
                "event", "REMINDER_CREATED",
                "reminderId", reminderId,
                "userId", userId,
                "title", title,
                "remindAt", remindAt.toString()
        ));
    }

    public void publishReminderDue(Long reminderId, Long userId, String title, LocalDateTime remindAt) {
        publish(reminderCreatedTopic, Map.of(
                "event", "REMINDER_DUE",
                "reminderId", reminderId,
                "userId", userId,
                "title", title,
                "remindAt", remindAt.toString()
        ));
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void publish(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            pubSubTemplate.publish(topic, json)
                    .addCallback(
                            messageId -> log.debug("Published to {}: messageId={}", topic, messageId),
                            ex -> log.error("Failed to publish to {}: {}", topic, ex.getMessage())
                    );
        } catch (Exception e) {
            log.error("Error serialising Pub/Sub payload for topic {}: {}", topic, e.getMessage());
        }
    }
}
