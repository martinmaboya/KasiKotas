
package kasiKotas.controller;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        int limitValue = limit.getLimitValue();
        int kotasOrderedToday = orderService.getTodaysKotasOrdered();
        int kotasOrderedAllTime = orderService.getAllTimeKotasOrdered();
        int remainingCapacity = Math.max(0, limitValue - kotasOrderedToday);

        Map<String, Object> response = Map.of(
            "id", limit.getId(),
            "limitValue", limitValue,
            "kotasOrderedToday", kotasOrderedToday,
            "kotasOrderedAllTime", kotasOrderedAllTime,
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
        if (requestBody == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        Integer limitValue = requestBody.get("limitValue");
        if (limitValue == null) {
            throw new IllegalArgumentException("limitValue is required.");
        }

        DailyOrderLimit savedLimit = dailyOrderLimitService.setOrderLimit(limitValue);
        return ResponseEntity.ok(savedLimit);
    }

    /**
     * Gets the count of kotas ordered today.
     * This is used by the frontend to display how many kotas have been ordered.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/todays-kotas")
    public ResponseEntity<Map<String, Integer>> getTodaysKotasOrdered() {
        int kotasOrdered = orderService.getTodaysKotasOrdered();
        int kotasOrderedAllTime = orderService.getAllTimeKotasOrdered();
        return ResponseEntity.ok(Map.of(
            "kotasOrdered", kotasOrdered,
            "kotasOrderedToday", kotasOrdered,
            "kotasOrderedAllTime", kotasOrderedAllTime
        ));
    }
}
