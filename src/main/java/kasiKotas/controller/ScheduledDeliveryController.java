package kasiKotas.controller;

import kasiKotas.model.Order;
import kasiKotas.service.DeliverySchedulingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for managing scheduled deliveries (Admin only).
 * Provides endpoints for viewing and managing scheduled orders.
 */
@RestController
@RequestMapping("/api/admin/scheduled-deliveries")
@PreAuthorize("hasAuthority('ADMIN')")
public class ScheduledDeliveryController {

    private final DeliverySchedulingService deliverySchedulingService;
    
    @Autowired
    public ScheduledDeliveryController(DeliverySchedulingService deliverySchedulingService) {
        this.deliverySchedulingService = deliverySchedulingService;
    }
    
    /**
     * Get all orders with scheduled delivery times.
     * @return List of orders that have scheduled delivery times
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllScheduledOrders() {
        List<Order> scheduledOrders = deliverySchedulingService.getAllScheduledOrders();
        return ResponseEntity.ok(scheduledOrders);
    }
    
    /**
     * Get orders scheduled for a specific time range.
     * @param start Start time in ISO format (e.g., 2025-07-22T09:00:00)
     * @param end End time in ISO format (e.g., 2025-07-22T17:00:00)
     * @param status Order status to filter by (optional)
     * @return List of orders in the specified time range
     */
    @GetMapping("/range")
    public ResponseEntity<List<Order>> getOrdersInTimeRange(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String status) {
        
        LocalDateTime startTime = LocalDateTime.parse(start);
        LocalDateTime endTime = LocalDateTime.parse(end);
        Order.OrderStatus orderStatus = status != null ? Order.OrderStatus.valueOf(status.toUpperCase()) : Order.OrderStatus.PENDING;
        
        List<Order> orders = deliverySchedulingService.getOrdersInTimeRange(startTime, endTime, orderStatus);
        return ResponseEntity.ok(orders);
    }
}
