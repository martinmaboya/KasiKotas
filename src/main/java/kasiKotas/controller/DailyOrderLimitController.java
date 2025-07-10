
package kasiKotas.controller;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.service.DailyOrderLimitService;
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

    @Autowired
    public DailyOrderLimitController(DailyOrderLimitService dailyOrderLimitService) {
        this.dailyOrderLimitService = dailyOrderLimitService;
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