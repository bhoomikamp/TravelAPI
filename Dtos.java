package com.travelapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.travelapi.model.Trip;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All DTO classes for TravelAPI.
 * Kept in one file for brevity; in a larger project, split into sub-packages.
 */
public class Dtos {

    // ─── Auth ────────────────────────────────────────────────────────────────

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank(message = "Full name is required")
        private String fullName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthResponse {
        private String token;
        private String tokenType;
        private Long userId;
        private String email;
        private String fullName;
    }

    // ─── Trip ────────────────────────────────────────────────────────────────

    @Data
    public static class TripRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Destination is required")
        private String destination;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;

        private String notes;

        private Trip.TripStatus status;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TripResponse {
        private Long id;
        private String title;
        private String destination;
        private LocalDate startDate;
        private LocalDate endDate;
        private String notes;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<ReminderResponse> reminders;
        private List<AttachmentResponse> attachments;
    }

    // ─── Reminder ────────────────────────────────────────────────────────────

    @Data
    public static class ReminderRequest {
        @NotNull  private Long tripId;
        @NotBlank private String title;
        @NotNull  private LocalDateTime remindAt;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReminderResponse {
        private Long id;
        private Long tripId;
        private String title;
        private LocalDateTime remindAt;
        private Boolean sent;
        private LocalDateTime createdAt;
    }

    // ─── Attachment ──────────────────────────────────────────────────────────

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AttachmentResponse {
        private Long id;
        private Long tripId;
        private String fileName;
        private String downloadUrl;   // signed GCS URL
        private String contentType;
        private Long sizeBytes;
        private LocalDateTime uploadedAt;
    }

    // ─── Analytics (BigQuery response) ────────────────────────────────────────

    @Data
    @Builder
    public static class TripAnalyticsResponse {
        private String destination;
        private Long tripCount;
        private Double avgDurationDays;
    }

    // ─── Generic API Wrapper ─────────────────────────────────────────────────

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private Integer totalCount;

        public static <T> ApiResponse<T> ok(T data) {
            return ApiResponse.<T>builder().success(true).data(data).build();
        }

        public static <T> ApiResponse<T> ok(T data, String message) {
            return ApiResponse.<T>builder().success(true).message(message).data(data).build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder().success(false).message(message).build();
        }
    }
}
