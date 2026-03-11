package com.travelapi.repository;

import com.travelapi.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ─── User Repository ──────────────────────────────────────────────────────────

@Repository
interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

// ─── Trip Repository ──────────────────────────────────────────────────────────

@Repository
interface TripRepository extends JpaRepository<Trip, Long> {

    Page<Trip> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.user.id = :userId AND t.status = :status")
    List<Trip> findByUserIdAndStatus(@Param("userId") Long userId,
                                     @Param("status") Trip.TripStatus status);

    @Query("SELECT t FROM Trip t WHERE t.user.id = :userId " +
           "AND LOWER(t.destination) LIKE LOWER(CONCAT('%', :destination, '%'))")
    List<Trip> searchByDestination(@Param("userId") Long userId,
                                   @Param("destination") String destination);

    // Bulk fetch with JOIN FETCH to avoid N+1
    @Query("SELECT DISTINCT t FROM Trip t " +
           "LEFT JOIN FETCH t.reminders " +
           "LEFT JOIN FETCH t.attachments " +
           "WHERE t.user.id = :userId")
    List<Trip> findAllWithDetailsForUser(@Param("userId") Long userId);
}

// ─── Reminder Repository ──────────────────────────────────────────────────────

@Repository
interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByTripId(Long tripId);

    // Fetch all unsent reminders due before a given time (used by scheduler)
    @Query("SELECT r FROM Reminder r WHERE r.sent = false AND r.remindAt <= :now")
    List<Reminder> findUnsentRemindersBeforeOrAt(@Param("now") LocalDateTime now);

    // Bulk mark as sent
    @Modifying
    @Query("UPDATE Reminder r SET r.sent = true WHERE r.id IN :ids")
    int markAsSent(@Param("ids") List<Long> ids);
}

// ─── Attachment Repository ────────────────────────────────────────────────────

@Repository
interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTripId(Long tripId);
}
