package kasiKotas.controller;

import kasiKotas.model.*;
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.CacheControl;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DailyOrderLimitService dailyOrderLimitService;

    @Autowired
    public OrderController(OrderService orderService, DailyOrderLimitService dailyOrderLimitService) {
        this.orderService = orderService;
        this.dailyOrderLimitService = dailyOrderLimitService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        orders.forEach(order -> {
            if (order.getUser() != null) {
                order.getUser().getId();
                order.getUser().getFirstName();
                order.getUser().getLastName();
                order.getUser().getEmail();
            }
            if (order.getOrderItems() != null) {
                order.getOrderItems().forEach(orderItem -> {
                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName();
                    }
                });
            }
        });
        return ResponseEntity.ok(orders);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(order -> {
                    if (order.getUser() != null) {
                        order.getUser().getId();
                        order.getUser().getFirstName();
                        order.getUser().getLastName();
                        order.getUser().getEmail();
                    }
                    if (order.getOrderItems() != null) {
                        order.getOrderItems().forEach(item -> {
                            if (item.getProduct() != null) {
                                item.getProduct().getName();
                            }
                        });
                    }
                    return ResponseEntity.ok(order);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('CUSTOMER') or #userId == authentication.principal.id")
    @GetMapping("/user/{userId}")
    @Cacheable(value = "userOrders", key = "#userId")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        System.out.println("Fetching orders for user: " + userId + " (cache miss)");
        
        try {
            List<Order> orders = orderService.getOrdersByUserIdOptimized(userId);
            
            // Add cache control headers
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(orders);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long orderId, @RequestBody Map<String, String> requestBody) {
        String statusString = requestBody.get("status");
        if (statusString == null || statusString.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            Order.OrderStatus newStatus = Order.OrderStatus.valueOf(statusString.toUpperCase());
            return orderService.updateOrderStatus(orderId, newStatus)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            System.err.println("Error updating order status: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during order status update: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        boolean deleted = orderService.deleteOrder(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/count")
    public ResponseEntity<Long> countTotalOrders() {
        long count = orderService.getTotalOrderCount();
        return ResponseEntity.ok(count);
    }

    // FIX: Change the return type from ResponseEntity<Order> to ResponseEntity<Object>
    // This allows returning both Order objects for success and Map<String, String> for error messages.
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Object> createOrder(@RequestBody Map<String, Object> orderRequest) {
        try {
            // ✅ Check current order count and limit
            long currentOrderCount = orderService.getTotalOrderCount();
            Optional<DailyOrderLimit> limitOptional = dailyOrderLimitService.getOrderLimit();

            if (limitOptional.isPresent()) {
                int limit = limitOptional.get().getLimitValue();
                if (limit == 0 || currentOrderCount >= limit) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Daily order limit reached. We are no longer taking orders for today."));
                }
            }

            // 🟢 Extract order data including promo code information
            Object userIdObj = orderRequest.get("userId");
            if (userIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Missing userId in order request"));
            }
            Long userId;
            try {
                userId = ((Number) userIdObj).longValue();
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid userId format"));
            }

            String shippingAddress = (String) orderRequest.get("shippingAddress");
            String paymentMethod = (String) orderRequest.get("paymentMethod");
            String deliveryMethod = (String) orderRequest.get("deliveryMethod");
            String promoCode = (String) orderRequest.get("promoCode");

            // Extract financial data from frontend calculation
            Double subtotal = null;
            Double deliveryFee = null;
            Double discountAmount = null;
            Double totalAmount = null;

            try {
                if (orderRequest.get("subtotal") != null) {
                    subtotal = ((Number) orderRequest.get("subtotal")).doubleValue();
                }
                if (orderRequest.get("deliveryFee") != null) {
                    deliveryFee = ((Number) orderRequest.get("deliveryFee")).doubleValue();
                }
                if (orderRequest.get("discountAmount") != null) {
                    discountAmount = ((Number) orderRequest.get("discountAmount")).doubleValue();
                }
                if (orderRequest.get("totalAmount") != null) {
                    totalAmount = ((Number) orderRequest.get("totalAmount")).doubleValue();
                }
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid financial data format"));
            }

            Object itemsRawObj = orderRequest.get("orderItems");
            if (itemsRawObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Missing orderItems in order request"));
            }
            List<Map<String, Object>> itemsRaw;
            try {
                itemsRaw = (List<Map<String, Object>>) itemsRawObj;
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid orderItems format"));
            }

            // Handle scheduled delivery time
            LocalDateTime scheduledDeliveryTime = null;
            Object scheduledTimeObj = orderRequest.get("scheduledDeliveryTime");
            if (scheduledTimeObj != null && !scheduledTimeObj.toString().isEmpty()) {
                try {
                    scheduledDeliveryTime = LocalDateTime.parse(scheduledTimeObj.toString());
                    validateScheduledDeliveryTime(scheduledDeliveryTime);
                } catch (DateTimeParseException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Invalid scheduled delivery time format"));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", e.getMessage()));
                }
            }

            Order newOrder = new Order();
            newOrder.setUser(new User(userId));
            newOrder.setShippingAddress(shippingAddress);
            newOrder.setPaymentMethod(paymentMethod);
            newOrder.setDeliveryMethod(deliveryMethod);
            newOrder.setScheduledDeliveryTime(scheduledDeliveryTime);

            // Set promo code and financial data from frontend
            newOrder.setPromoCode(promoCode);
            newOrder.setSubtotal(subtotal);
            newOrder.setDeliveryFee(deliveryFee);
            newOrder.setDiscountAmount(discountAmount);
            newOrder.setTotalAmount(totalAmount); // Use the calculated total from frontend

            List<OrderItem> orderItems = new ArrayList<>();
            for (Map<String, Object> itemRaw : itemsRaw) {
                OrderItem item = new OrderItem();
                Map<String, Object> productMap = (Map<String, Object>) itemRaw.get("product");
                if (productMap != null && productMap.containsKey("id")) {
                    Object productIdObj = productMap.get("id");
                    if (productIdObj == null) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Missing product id in order item"));
                    }
                    try {
                        Product product = new Product();
                        product.setId(((Number) productIdObj).longValue());
                        item.setProduct(product);
                    } catch (Exception ex) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Invalid product id format in order item"));
                    }
                }
                Object quantityObj = itemRaw.get("quantity");
                if (quantityObj == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Missing quantity in order item"));
                }
                try {
                    item.setQuantity(((Number) quantityObj).intValue());
                } catch (Exception ex) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Invalid quantity format in order item"));
                }
                item.setCustomizationNotes((String) itemRaw.get("customizationNotes"));
                item.setSelectedExtrasJson((String) itemRaw.get("selectedExtrasJson"));
                item.setSelectedSaucesJson((String) itemRaw.get("selectedSaucesJson"));
                orderItems.add(item);
            }
            newOrder.setOrderItems(orderItems);

            Order savedOrder = orderService.createOrder(newOrder);

            return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            System.err.println("Order creation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during order creation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during order creation."));
        }
    }
    
    /**
     * Validates the scheduled delivery time according to business rules
     * @param scheduledTime The scheduled delivery time to validate
     * @throws IllegalArgumentException if the scheduled time is invalid
     */
    private void validateScheduledDeliveryTime(LocalDateTime scheduledTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxScheduleTime = now.plusDays(7);
        
        if (scheduledTime.isBefore(now)) {
            throw new IllegalArgumentException("Scheduled delivery time must be in the future");
        }
        
        if (scheduledTime.isAfter(maxScheduleTime)) {
            throw new IllegalArgumentException("Scheduled delivery can only be set up to 7 days in advance");
        }

        // Validate business hours (6 PM - 11:59 PM)
        int hour = scheduledTime.getHour();
        int minute = scheduledTime.getMinute();
        if (hour < 18 || (hour == 23 && minute > 59) || hour > 23) {
            throw new IllegalArgumentException("Scheduled delivery must be between 18:00 and 23:59");
        }

    }
}