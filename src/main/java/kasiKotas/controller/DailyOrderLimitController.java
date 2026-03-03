
package kasiKotas.controller;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/order-limit")
public class DailyOrderLimitController {

    private final DailyOrderLimitService dailyOrderLimitService;
    private final OrderService orderService;

    @Autowired
    public DailyOrderLimitController(DailyOrderLimitService dailyOrderLimitService, OrderService orderService) {
        this.dailyOrderLimitService = dailyOrderLimitService;
        this.orderService = orderService;
    }

    /**
     * Retrieves the current total order limit.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrderLimit() {
        Optional<DailyOrderLimit> limitOptional = dailyOrderLimitService.getOrderLimit();
        
        if (limitOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        DailyOrderLimit limit = limitOptional.get();
        // limitValue IS the remaining capacity (it gets decremented as orders come in)
        int remainingCapacity = limit.getLimitValue();
        int kotasOrderedToday = orderService.getTodaysKotasOrdered();

        Map<String, Object> response = Map.of(
            "id", limit.getId(),
            "limitValue", remainingCapacity,
            "kotasOrderedToday", kotasOrderedToday,
            "remainingCapacity", remainingCapacity
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Sets or updates the total order limit.
     * This sets the TOTAL capacity allowed for the day.
     * For example, if you set limit to 20 and 26 kotas were already ordered,
     * no more orders will be accepted (26 > 20).
     * If you want to allow 20 more when 26 are ordered, set limit to 46.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DailyOrderLimit> setOrderLimit(@RequestBody Map<String, Integer> requestBody) {
        Integer limitValue = requestBody.get("limitValue");
        if (limitValue == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // Set the limit as total capacity for the day
            DailyOrderLimit savedLimit = dailyOrderLimitService.setOrderLimit(limitValue);
            return ResponseEntity.ok(savedLimit);
        } catch (IllegalArgumentException e) {
            System.err.println("Error setting order limit: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets the count of kotas ordered today.
     * This is used by the frontend to display how many kotas have been ordered.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/todays-kotas")
    public ResponseEntity<Map<String, Integer>> getTodaysKotasOrdered() {
        try {
            int kotasOrdered = orderService.getTodaysKotasOrdered();
            return ResponseEntity.ok(Map.of("kotasOrdered", kotasOrdered));
        } catch (Exception e) {
            System.err.println("Error getting today's kotas: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Debug endpoint: shows the server's current time and today's date window used for counting.
     * Use this to diagnose timezone mismatches between the server and your local clock.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> getDebugInfo() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        int kotasInWindow = orderService.getTodaysKotasOrdered();
        return ResponseEntity.ok(Map.of(
            "serverNow", now.toString(),
            "queryWindowStart", startOfDay.toString(),
            "queryWindowEnd", endOfDay.toString(),
            "kotasFoundInWindow", kotasInWindow,
            "recentOrderDates", orderService.getRecentOrderDates()
        ));
    }
}
