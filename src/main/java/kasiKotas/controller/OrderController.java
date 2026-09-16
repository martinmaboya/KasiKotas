package kasiKotas.controller;

import kasiKotas.model.*;
import kasiKotas.service.OrderService;
import kasiKotas.service.PaymentService;
import kasiKotas.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final PaymentService paymentService;

    @Autowired
    public OrderController(
            OrderService orderService,
            UserService userService,
            PaymentService paymentService) {

        this.orderService = orderService;
        this.userService = userService;
        this.paymentService = paymentService;
    }

    // =========================================================
    // GET ALL ORDERS
    // =========================================================

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

    // =========================================================
    // GET SINGLE ORDER
    // =========================================================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(
            @PathVariable Long id) {

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
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =========================================================
    // GET USER ORDERS
    // =========================================================

    @PreAuthorize("@authorizationHelper.canAccessUser(authentication, #userId)")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(
            @PathVariable Long userId) {

        List<Order> orders =
                orderService.getOrdersByUserIdOptimized(userId);

        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.maxAge(
                                30,
                                TimeUnit.SECONDS
                        )
                )
                .body(orders);
    }

    // =========================================================
    // UPDATE ORDER STATUS
    // =========================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> requestBody) {

        if (requestBody == null) {
            throw new IllegalArgumentException(
                    "Request body is required."
            );
        }

        String statusString = requestBody.get("status");

        if (statusString == null || statusString.isBlank()) {
            throw new IllegalArgumentException(
                    "status is required."
            );
        }

        Order.OrderStatus newStatus;

        try {

            newStatus =
                    Order.OrderStatus.valueOf(
                            statusString.trim().toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid order status: " + statusString
            );
        }

        return orderService
                .updateOrderStatus(orderId, newStatus)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =========================================================
    // DELETE ORDER
    // =========================================================

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id) {

        boolean deleted =
                orderService.deleteOrder(id);

        if (deleted) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    // =========================================================
    // COUNT ORDERS
    // =========================================================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/count")
    public ResponseEntity<Long> countTotalOrders() {

        long count =
                orderService.getTotalOrderCount();

        return ResponseEntity.ok(count);
    }

    // =========================================================
    // CREATE ORDER
    // =========================================================

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Object> createOrder(
            @RequestBody Map<String, Object> orderRequest) {

        if (orderRequest == null) {
            throw new IllegalArgumentException(
                    "Order payload is required."
            );
        }

        // -----------------------------------------------------
        // BASIC REQUEST FIELDS
        // -----------------------------------------------------

        String shippingAddress =
                asString(
                        orderRequest.get("shippingAddress"),
                        "shippingAddress",
                        false
                );

        String paymentMethodRaw =
                asString(
                        orderRequest.get("paymentMethod"),
                        "paymentMethod",
                        true
                );

        String deliveryMethod =
                asString(
                        orderRequest.get("deliveryMethod"),
                        "deliveryMethod",
                        false
                );

        String promoCode =
                asString(
                        orderRequest.get("promoCode"),
                        "promoCode",
                        false
                );

        String idempotencyKey =
                asString(
                        orderRequest.get("idempotencyKey"),
                        "idempotencyKey",
                        false
                );

        List<Map<String, Object>> itemsRaw =
                asListOfMaps(
                        orderRequest.get("orderItems"),
                        "orderItems"
                );

        LocalDateTime scheduledDeliveryTime =
                parseScheduledDeliveryTime(
                        orderRequest.get("scheduledDeliveryTime")
                );

        // -----------------------------------------------------
        // CONVERT PAYMENT METHOD TO ENUM
        // -----------------------------------------------------

        PaymentMethod paymentMethod;

        try {

            paymentMethod =
                    PaymentMethod.valueOf(
                            paymentMethodRaw.trim().toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid payment method. Allowed values: YOCO, EFT, COD"
            );
        }

        // -----------------------------------------------------
        // AUTHENTICATED USER
        // -----------------------------------------------------

        User authenticatedUser =
                resolveAuthenticatedUser();

        // -----------------------------------------------------
        // CREATE ORDER OBJECT
        // -----------------------------------------------------

        Order newOrder = new Order();

        newOrder.setUser(
                new User(authenticatedUser.getId())
        );

        newOrder.setShippingAddress(
                shippingAddress
        );

        /*
         * paymentMethod is no longer stored directly on Order.
         *
         * It is passed separately to OrderService and then
         * stored on the Payment entity.
         */

        newOrder.setDeliveryMethod(
                deliveryMethod
        );

        newOrder.setScheduledDeliveryTime(
                scheduledDeliveryTime
        );

        newOrder.setPromoCode(
                promoCode
        );

        // -----------------------------------------------------
        // IDEMPOTENCY KEY
        // -----------------------------------------------------

        if (idempotencyKey != null
                && !idempotencyKey.isBlank()) {

            newOrder.setIdempotencyKey(
                    idempotencyKey.trim()
            );
        }

        // -----------------------------------------------------
        // BUILD ORDER ITEMS
        // -----------------------------------------------------

        List<OrderItem> orderItems =
                new ArrayList<>();

        for (Map<String, Object> itemRaw : itemsRaw) {

            OrderItem item =
                    new OrderItem();

            Map<String, Object> productMap =
                    asMap(
                            itemRaw.get("product"),
                            "orderItems[].product"
                    );

            Long productId =
                    asLong(
                            productMap.get("id"),
                            "orderItems[].product.id"
                    );

            Product product =
                    new Product();

            product.setId(productId);

            item.setProduct(product);

            item.setQuantity(
                    asInteger(
                            itemRaw.get("quantity"),
                            "orderItems[].quantity"
                    )
            );

            item.setCustomizationNotes(
                    asString(
                            itemRaw.get("customizationNotes"),
                            "orderItems[].customizationNotes",
                            false
                    )
            );

            item.setSelectedExtrasJson(
                    asString(
                            itemRaw.get("selectedExtrasJson"),
                            "orderItems[].selectedExtrasJson",
                            false
                    )
            );

            item.setSelectedSaucesJson(
                    asString(
                            itemRaw.get("selectedSaucesJson"),
                            "orderItems[].selectedSaucesJson",
                            false
                    )
            );

            orderItems.add(item);
        }

        newOrder.setOrderItems(orderItems);

        // -----------------------------------------------------
        // CREATE ORDER
        // -----------------------------------------------------

        Order savedOrder =
                orderService.createOrder(
                        newOrder,
                        paymentMethod
                );

        // -----------------------------------------------------
        // CREATE PAYMENT
        // -----------------------------------------------------

        Payment payment =
                paymentService.createPayment(
                        savedOrder,
                        paymentMethod,
                        savedOrder.getTotalAmount()
                );

        /*
         * Set the relationship on the Java object as well.
         *
         * Payment owns the database relationship because
         * Payment.order contains @JoinColumn.
         */
        savedOrder.setPayment(payment);

        // -----------------------------------------------------
        // RETURN CREATED ORDER
        // -----------------------------------------------------

        return new ResponseEntity<>(
                savedOrder,
                HttpStatus.CREATED
        );
    }

    // =========================================================
    // RESOLVE AUTHENTICATED USER
    // =========================================================

    private User resolveAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        String email;

        if (principal instanceof UserDetails userDetails) {

            email = userDetails.getUsername();

        } else if (principal instanceof String username
                && !"anonymousUser".equalsIgnoreCase(username)) {

            email = username;

        } else {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        return userService
                .getUserByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user not found"
                        )
                );
    }

    // =========================================================
    // SCHEDULED DELIVERY TIME
    // =========================================================

    private LocalDateTime parseScheduledDeliveryTime(
            Object scheduledTimeObj) {

        if (scheduledTimeObj == null
                || scheduledTimeObj.toString().isBlank()) {

            return null;
        }

        String scheduledTimeStr =
                scheduledTimeObj.toString();

        try {

            LocalDateTime scheduledDeliveryTime;

            if (scheduledTimeStr.length() == 19
                    && scheduledTimeStr.contains("T")) {

                scheduledDeliveryTime =
                        LocalDateTime.parse(
                                scheduledTimeStr
                        );

            } else if (scheduledTimeStr.contains("T")
                    && scheduledTimeStr.contains("Z")) {

                scheduledDeliveryTime =
                        LocalDateTime.parse(
                                scheduledTimeStr.replace("Z", "")
                        );

            } else if (scheduledTimeStr.contains("T")
                    && scheduledTimeStr.contains("+")) {

                scheduledDeliveryTime =
                        LocalDateTime.parse(
                                scheduledTimeStr.substring(0, 19)
                        );

            } else {

                scheduledDeliveryTime =
                        LocalDateTime.parse(
                                scheduledTimeStr
                        );
            }

            validateScheduledDeliveryTime(
                    scheduledDeliveryTime
            );

            return scheduledDeliveryTime;

        } catch (DateTimeParseException e) {

            throw new IllegalArgumentException(
                    "Invalid scheduled delivery time format: "
                            + scheduledTimeObj
            );
        }
    }

    // =========================================================
    // MAP VALIDATION
    // =========================================================

    private Map<String, Object> asMap(
            Object value,
            String fieldName) {

        if (!(value instanceof Map<?, ?> rawMap)) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format."
            );
        }

        for (Object key : rawMap.keySet()) {

            if (!(key instanceof String)) {

                throw new IllegalArgumentException(
                        "Invalid " + fieldName + " format."
                );
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> safeMap =
                (Map<String, Object>) rawMap;

        return safeMap;
    }

    private List<Map<String, Object>> asListOfMaps(
            Object value,
            String fieldName) {

        if (!(value instanceof List<?> listValue)) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format."
            );
        }

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Object element : listValue) {

            result.add(
                    asMap(
                            element,
                            fieldName + "[]"
                    )
            );
        }

        return result;
    }

    // =========================================================
    // TYPE CONVERSION
    // =========================================================

    private Long asLong(
            Object value,
            String fieldName) {

        if (!(value instanceof Number numberValue)) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format."
            );
        }

        return numberValue.longValue();
    }

    private Integer asInteger(
            Object value,
            String fieldName) {

        if (!(value instanceof Number numberValue)) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format."
            );
        }

        return numberValue.intValue();
    }

    private String asString(
            Object value,
            String fieldName,
            boolean required) {

        if (value == null) {

            if (required) {

                throw new IllegalArgumentException(
                        fieldName + " is required."
                );
            }

            return null;
        }

        if (!(value instanceof String textValue)) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format."
            );
        }

        if (required && textValue.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName + " is required."
            );
        }

        return Objects.equals(
                textValue,
                ""
        ) ? null : textValue;
    }

    // =========================================================
    // VALIDATE SCHEDULED TIME
    // =========================================================

    /**
     * Validates the scheduled time according to business rules.
     * Applies to both delivery and collection.
     */
    private void validateScheduledDeliveryTime(
            LocalDateTime scheduledTime) {

        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime maxScheduleTime =
                now.plusDays(7);

        if (scheduledTime.isBefore(now)) {

            throw new IllegalArgumentException(
                    "Scheduled time must be in the future"
            );
        }

        if (scheduledTime.isAfter(maxScheduleTime)) {

            throw new IllegalArgumentException(
                    "Scheduled time can only be set up to 7 days in advance"
            );
        }

        int hour =
                scheduledTime.getHour();

        if (hour < 18 || hour > 23) {

            throw new IllegalArgumentException(
                    "Scheduled time must be between 18:00 and 23:59"
            );
        }
    }
}