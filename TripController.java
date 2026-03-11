package com.travelapi.controller;

import com.travelapi.dto.Dtos.*;
import com.travelapi.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /**
     * POST /api/trips
     * Create a new trip.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(
            @Valid @RequestBody TripRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TripResponse trip = tripService.createTrip(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(trip, "Trip created successfully"));
    }

    /**
     * GET /api/trips?page=0&size=10&sortBy=createdAt
     * List all trips for authenticated user (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @AuthenticationPrincipal UserDetails userDetails) {
        Page<TripResponse> trips = tripService.getTrips(userDetails.getUsername(), page, size, sortBy);
        return ResponseEntity.ok(ApiResponse.<Page<TripResponse>>builder()
                .success(true).data(trips).totalCount((int) trips.getTotalElements()).build());
    }

    /**
     * GET /api/trips/{id}
     * Get a specific trip with reminders and attachments.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> getTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        TripResponse trip = tripService.getTripById(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(trip));
    }

    /**
     * GET /api/trips/search?destination=Paris
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TripResponse>>> search(
            @RequestParam String destination,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<TripResponse> trips = tripService.searchByDestination(destination, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(trips));
    }

    /**
     * PUT /api/trips/{id}
     * Update a trip.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> updateTrip(
            @PathVariable Long id,
            @Valid @RequestBody TripRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TripResponse trip = tripService.updateTrip(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(trip, "Trip updated successfully"));
    }

    /**
     * DELETE /api/trips/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        tripService.deleteTrip(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null, "Trip deleted successfully"));
    }
}
