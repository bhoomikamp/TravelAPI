package com.travelapi;

import com.travelapi.dto.Dtos.*;
import com.travelapi.exception.ApiException;
import com.travelapi.model.*;
import com.travelapi.repository.Repositories;
import com.travelapi.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelApiTests {

    // ─── TripService Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("TripService")
    class TripServiceTests {

        @Mock private Repositories.TripRepo tripRepository;
        @Mock private Repositories.UserRepo userRepository;
        @Mock private PubSubService pubSubService;

        @InjectMocks private TripService tripService;

        private User testUser;
        private Trip testTrip;

        @BeforeEach
        void setUp() {
            testUser = User.builder()
                    .id(1L)
                    .email("bhoomika@test.com")
                    .fullName("Bhoomika MP")
                    .password("hashed")
                    .build();

            testTrip = Trip.builder()
                    .id(10L)
                    .user(testUser)
                    .title("Japan Adventure")
                    .destination("Tokyo, Japan")
                    .startDate(LocalDate.of(2025, 3, 1))
                    .endDate(LocalDate.of(2025, 3, 15))
                    .status(Trip.TripStatus.PLANNED)
                    .build();
        }

        @Test
        @DisplayName("createTrip – saves trip and publishes Pub/Sub event")
        void createTrip_success() {
            TripRequest request = new TripRequest();
            request.setTitle("Japan Adventure");
            request.setDestination("Tokyo, Japan");
            request.setStartDate(LocalDate.of(2025, 3, 1));
            request.setEndDate(LocalDate.of(2025, 3, 15));

            when(userRepository.findByEmail("bhoomika@test.com")).thenReturn(Optional.of(testUser));
            when(tripRepository.save(any(Trip.class))).thenReturn(testTrip);
            doNothing().when(pubSubService).publishTripCreated(anyLong(), anyLong(), anyString());

            TripResponse response = tripService.createTrip(request, "bhoomika@test.com");

            assertThat(response.getTitle()).isEqualTo("Japan Adventure");
            assertThat(response.getDestination()).isEqualTo("Tokyo, Japan");
            assertThat(response.getStatus()).isEqualTo("PLANNED");

            verify(tripRepository, times(1)).save(any(Trip.class));
            verify(pubSubService, times(1)).publishTripCreated(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("createTrip – throws BAD_REQUEST when end date before start date")
        void createTrip_invalidDateRange() {
            TripRequest request = new TripRequest();
            request.setTitle("Bad Dates");
            request.setDestination("Paris");
            request.setStartDate(LocalDate.of(2025, 5, 10));
            request.setEndDate(LocalDate.of(2025, 5, 1));   // End before start

            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> tripService.createTrip(request, "bhoomika@test.com"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("End date cannot be before start date")
                    .extracting(ex -> ((ApiException) ex).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            verify(tripRepository, never()).save(any());
        }

        @Test
        @DisplayName("getTripById – returns trip for valid owner")
        void getTripById_success() {
            when(tripRepository.findById(10L)).thenReturn(Optional.of(testTrip));

            TripResponse response = tripService.getTripById(10L, "bhoomika@test.com");

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getTitle()).isEqualTo("Japan Adventure");
        }

        @Test
        @DisplayName("getTripById – throws FORBIDDEN for wrong owner")
        void getTripById_wrongOwner() {
            when(tripRepository.findById(10L)).thenReturn(Optional.of(testTrip));

            assertThatThrownBy(() -> tripService.getTripById(10L, "other@user.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("getTrips – returns paginated results")
        void getTrips_paginated() {
            Page<Trip> page = new PageImpl<>(List.of(testTrip));
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(tripRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

            Page<TripResponse> result = tripService.getTrips("bhoomika@test.com", 0, 10, "createdAt");

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Japan Adventure");
        }

        @Test
        @DisplayName("deleteTrip – deletes trip for valid owner")
        void deleteTrip_success() {
            when(tripRepository.findById(10L)).thenReturn(Optional.of(testTrip));

            tripService.deleteTrip(10L, "bhoomika@test.com");

            verify(tripRepository, times(1)).delete(testTrip);
        }

        @Test
        @DisplayName("getTripById – throws NOT_FOUND for missing trip")
        void getTripById_notFound() {
            when(tripRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tripService.getTripById(99L, "bhoomika@test.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── ReminderService Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("ReminderService")
    class ReminderServiceTests {

        @Mock private Repositories.ReminderRepo reminderRepository;
        @Mock private Repositories.TripRepo tripRepository;
        @Mock private Repositories.UserRepo userRepository;
        @Mock private PubSubService pubSubService;

        @InjectMocks private ReminderService reminderService;

        @Test
        @DisplayName("processReminders – bulk marks as sent and publishes events")
        void processReminders_marksSentAndPublishes() {
            User user = User.builder().id(1L).email("test@test.com").build();
            Trip trip = Trip.builder().id(1L).user(user).build();

            com.travelapi.model.Reminder r1 = com.travelapi.model.Reminder.builder()
                    .id(1L).user(user).trip(trip)
                    .title("Pack bags").remindAt(java.time.LocalDateTime.now().minusMinutes(1))
                    .sent(false).build();
            com.travelapi.model.Reminder r2 = com.travelapi.model.Reminder.builder()
                    .id(2L).user(user).trip(trip)
                    .title("Book taxi").remindAt(java.time.LocalDateTime.now().minusMinutes(2))
                    .sent(false).build();

            when(reminderRepository.findUnsentRemindersBeforeOrAt(any())).thenReturn(List.of(r1, r2));
            when(reminderRepository.markAsSent(anyList())).thenReturn(2);

            reminderService.processReminders();

            verify(pubSubService, times(2)).publishReminderDue(anyLong(), anyLong(), anyString(), any());
            verify(reminderRepository, times(1)).markAsSent(List.of(1L, 2L));
        }

        @Test
        @DisplayName("processReminders – no-op when no due reminders")
        void processReminders_noOp() {
            when(reminderRepository.findUnsentRemindersBeforeOrAt(any())).thenReturn(List.of());

            reminderService.processReminders();

            verifyNoInteractions(pubSubService);
            verify(reminderRepository, never()).markAsSent(any());
        }
    }
}
