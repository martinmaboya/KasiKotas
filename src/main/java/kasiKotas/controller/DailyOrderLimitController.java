
package kasiKotas.controller;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public ResponseEntity<DailyOrderLimit> getOrderLimit() {
        return dailyOrderLimitService.getOrderLimit()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Sets or updates the total order limit.
     * This automatically accounts for kotas already ordered today.
     * For example, if you set limit to 20 and 26 kotas were already ordered,
     * the remaining capacity will be 0.
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
            // Use the smart method that accounts for today's orders
            DailyOrderLimit savedLimit = dailyOrderLimitService.setOrderLimitAccountingForToday(limitValue);
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
}
