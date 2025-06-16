// src/main/java/kasiKotas/controller/OrderController.java
package kasiKotas.controller;

import kasiKotas.model.Order;
import kasiKotas.model.OrderItem; // Make sure OrderItem is imported
import kasiKotas.model.Product; // Also need Product for creating OrderItem
import kasiKotas.model.User; // Need User if we're going to access its properties explicitly
import kasiKotas.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // For status update request and create order request body

/**
 * REST Controller for managing Order related operations.
 * Exposes API endpoints for creating, retrieving, and updating orders.
 */
@RestController
@RequestMapping("/api/orders") // Base path for order endpoints
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order.
     * POST /api/orders
     * @param orderRequest An object containing userId, shippingAddress, and a list of orderItems (with productId and quantity).
     * @return ResponseEntity with the created Order (201 Created) or 400 Bad Request if validation fails.
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> orderRequest) {
        try {
            Long userId = ((Number) orderRequest.get("userId")).longValue();
            String shippingAddress = (String) orderRequest.get("shippingAddress");
            List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) orderRequest.get("orderItems");

            // Convert raw item maps to OrderItem objects
            List<OrderItem> orderItems = new java.util.ArrayList<>();
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

                orderItems.add(item);
            }

            Order savedOrder = orderService.createOrder(userId, orderItems, shippingAddress);

            // --- IMPORTANT: Ensure lazy-loaded associations are initialized before returning ---
            // This explicitly loads the User and Product for all order items within the transactional context
            // to prevent LazyInitializationException during JSON serialization if fetch type is LAZY.
            // The NullPointerException suggests savedOrder.getUser() is *literally* null,
            // which implies a deeper issue if OrderService correctly sets it.
            // However, this block is good practice for returning complex entities and resolves cases
            // where proxies might not be initialized.
            if (savedOrder.getUser() != null) {
                // Access a property to force initialization of User proxy
                // If it's still null here, there's a problem earlier in the service or data mapping.
                savedOrder.getUser().getId();
            }
            if (savedOrder.getOrderItems() != null) {
                savedOrder.getOrderItems().forEach(item -> {
                    if (item.getProduct() != null) {
                        // Access a property to force initialization of Product proxy
                        item.getProduct().getName();
                    }
                });
            }

            return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            System.err.println("Order creation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(null); // Return 400 Bad Request with null body for consistency
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during order creation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Return 500 Internal Server Error
        }
    }

    /**
     * Retrieves all orders (typically for admin use).
     * GET /api/orders
     * @return A list of all Order objects.
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        // Eagerly load user and products within order items for each order before returning
        orders.forEach(order -> {
            if (order.getUser() != null) {
                order.getUser().getId(); // Force initialize user
            }
            if (order.getOrderItems() != null) {
                order.getOrderItems().forEach(orderItem -> {
                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName(); // Force initialize product
                    }
                });
            }
        });
        return ResponseEntity.ok(orders);
    }

    /**
     * Retrieves a single order by its ID.
     * GET /api/orders/{id}
     * @param id The ID of the order to retrieve.
     * @return An Optional containing the Order if found, or empty if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(order -> {
                    // Ensure user and products are initialized before returning
                    if (order.getUser() != null) {
                        order.getUser().getId();
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

    /**
     * Retrieves all orders for a specific user.
     * GET /api/orders/user/{userId}
     * @param userId The ID of the user whose orders to retrieve.
     * @return A list of Order objects belonging to the user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        try {
            List<Order> orders = orderService.getOrdersByUserId(userId);
            // Ensure user and products are initialized for each order
            orders.forEach(order -> {
                if (order.getUser() != null) {
                    order.getUser().getId();
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

    /**
     * Updates the status of an existing order.
     * PUT /api/orders/{orderId}/status
     * @param orderId The ID of the order to update.
     * @param requestBody A map containing the "status" string (e.g., {"status": "DELIVERED"}).
     * @return An Optional containing the updated Order if found, or empty if not found.
     */
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

    /**
     * Deletes an order by its ID.
     * DELETE /api/orders/{id}
     * @param id The ID of the order to delete.
     * @return true if the order was found and deleted, false otherwise.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        boolean deleted = orderService.deleteOrder(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Counts the total number of orders in the system (all time).
     * GET /api/orders/count
     * @return The total count of orders.
     */
    @GetMapping("/count") // NEW: Specific endpoint for counting orders
    public ResponseEntity<Long> countTotalOrders() {
        long count = orderService.countTotalOrders();
        return ResponseEntity.ok(count);
    }
}
