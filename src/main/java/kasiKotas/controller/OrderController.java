
package kasiKotas.controller;

import kasiKotas.model.*;
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;


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

    @PreAuthorize("hasRole('CUSTOMER') or #userId == authentication.principal.id")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        try {
            List<Order> orders = orderService.getOrdersByUserId(userId);
            orders.forEach(order -> {
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
            });
            return ResponseEntity.ok(orders);
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> orderRequest) {
        try {
            // ✅ Check current order count and limit
            long currentOrderCount = orderService.getTotalOrderCount(); // You already have this method
            Optional<DailyOrderLimit> limitOptional = dailyOrderLimitService.getOrderLimit(); // You have this in DailyOrderLimitService

            if (limitOptional.isPresent()) {
                int limit = limitOptional.get().getLimitValue();
                if (limit == 0 || currentOrderCount >= limit) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(null); // or return a message saying "Order limit reached"
                }
            }

            // 🟢 Continue with normal order creation
            Long userId = ((Number) orderRequest.get("userId")).longValue();
            String shippingAddress = (String) orderRequest.get("shippingAddress");
            String paymentMethod = (String) orderRequest.get("paymentMethod");
            List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) orderRequest.get("orderItems");

            Order newOrder = new Order();
            newOrder.setUser(new User(userId));
            newOrder.setShippingAddress(shippingAddress);
            newOrder.setPaymentMethod(paymentMethod);

            List<OrderItem> orderItems = new ArrayList<>();
            for (Map<String, Object> itemRaw : itemsRaw) {
                OrderItem item = new OrderItem();
                Map<String, Object> productMap = (Map<String, Object>) itemRaw.get("product");
                if (productMap != null && productMap.containsKey("id")) {
                    Product product = new Product();
                    product.setId(((Number) productMap.get("id")).longValue());
                    item.setProduct(product);
                }
                item.setQuantity(((Number) itemRaw.get("quantity")).intValue());
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
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during order creation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}