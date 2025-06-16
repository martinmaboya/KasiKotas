// src/main/java/kasiKotas/controller/DailyOrderLimitController.java
package kasiKotas.controller;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.service.DailyOrderLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for managing the Total Order Limit.
 * This controller provides endpoints for administrators to retrieve and set
 * the maximum total number of orders allowed.
 *
 * IMPORTANT: In a real application, these endpoints MUST be protected
 * with Spring Security to ensure only authenticated ADMIN users can access them.
 * For now, they are publicly accessible for development purposes.
 */
@RestController
@RequestMapping("/api/order-limit") // CORRECTED: Changed base path to match frontend calls
public class DailyOrderLimitController {

    private final DailyOrderLimitService dailyOrderLimitService;

    @Autowired
    public DailyOrderLimitController(DailyOrderLimitService dailyOrderLimitService) {
        this.dailyOrderLimitService = dailyOrderLimitService;
    }

    /**
     * Retrieves the current total order limit.
     * GET /api/order-limit
     * @return ResponseEntity with DailyOrderLimit (200 OK) or 404 Not Found if not set.
     */
    @GetMapping
    public ResponseEntity<DailyOrderLimit> getOrderLimit() {
        return dailyOrderLimitService.getOrderLimit()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Sets or updates the total order limit.
     * POST /api/order-limit
     * This endpoint is intended for use by administrators to set/modify the limit.
     * Expected request body: {"limitValue": 50}
     *
     * IMPORTANT: This endpoint needs proper authentication and authorization (ADMIN role).
     * @param requestBody A map containing the "limitValue" (integer).
     * @return ResponseEntity with the saved/updated DailyOrderLimit (200 OK)
     * or 400 Bad Request if validation fails.
     */
    @PostMapping
    public ResponseEntity<DailyOrderLimit> setOrderLimit(@RequestBody Map<String, Integer> requestBody) {
        Integer limitValue = requestBody.get("limitValue");
        if (limitValue == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
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
}
