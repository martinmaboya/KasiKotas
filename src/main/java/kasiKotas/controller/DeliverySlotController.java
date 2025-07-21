package kasiKotas.controller;

import kasiKotas.service.DeliverySchedulingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing delivery time slots.
 * Provides endpoints to check available delivery slots.
 */
@RestController
@RequestMapping("/api/delivery-slots")
public class DeliverySlotController {

    private final DeliverySchedulingService deliverySchedulingService;

    @Autowired
    public DeliverySlotController(DeliverySchedulingService deliverySchedulingService) {
        this.deliverySchedulingService = deliverySchedulingService;
    }

    /**
     * Get available delivery time slots for a specific date.
     * This is a basic implementation - you can enhance it based on your business requirements.
     *
     * @param date Date in YYYY-MM-DD format
     * @return List of available time slots
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/available")
    public ResponseEntity<Object> getAvailableTimeSlots(@RequestParam String date) {
        try {
            LocalDate deliveryDate = LocalDate.parse(date);
            LocalDate today = LocalDate.now();

            // Don't allow scheduling for past dates
            if (deliveryDate.isBefore(today)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Cannot schedule delivery for past dates"));
            }

            // Don't allow scheduling more than 7 days in advance
            if (deliveryDate.isAfter(today.plusDays(7))) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Cannot schedule delivery more than 7 days in advance"));
            }

            List<String> availableSlots = generateTimeSlots(deliveryDate);
            return ResponseEntity.ok(availableSlots);

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid date format. Use YYYY-MM-DD"));
        }
    }

    /**
     * Generates available time slots for a given date.
     * Business hours: 9 AM to 8 PM, with 1-hour slots.
     * This is a basic implementation - you can enhance it to check actual availability.
     *
     * @param date The date to generate slots for
     * @return List of time slot strings
     */
    private List<String> generateTimeSlots(LocalDate date) {
        List<String> slots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Generate hourly slots from 18:00 (6 PM) to 23:00 (11 PM)
        for (int hour = 18; hour <= 23; hour++) {
            LocalDateTime slotTime = date.atTime(hour, 0);

            // If it's today, only include future slots
            if (date.equals(LocalDate.now()) && slotTime.isBefore(now.plusHours(1))) {
                continue;
            }

            // Format time slot (e.g., "18:00")
            String timeSlot = String.format("%02d:00", hour);
            slots.add(timeSlot);
        }

        // Optionally add 23:59 slot
        LocalDateTime lastSlot = date.atTime(23, 59);
        if (!(date.equals(LocalDate.now()) && lastSlot.isBefore(now.plusHours(1)))) {
            slots.add("23:59");
        }

        return slots;
    }
}