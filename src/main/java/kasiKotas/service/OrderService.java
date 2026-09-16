// src/main/java/kasiKotas/service/OrderService.java
package kasiKotas.service;

import kasiKotas.exception.ConcurrencyConflictException;
import kasiKotas.exception.IdempotencyConflictException;
import kasiKotas.exception.InsufficientStockException;
import kasiKotas.exception.OrderLimitExceededException;
import kasiKotas.model.*;
import kasiKotas.model.PromoCode;
import kasiKotas.repository.OrderRepository;
import kasiKotas.repository.OrderItemRepository;
import kasiKotas.repository.UserRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.ExtraRepository;
import kasiKotas.repository.ProductExtraRequirementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service layer for managing Order related business logic.
 *
 * This class handles:
 * - Creating orders
 * - Validating users and products
 * - Daily order limits
 * - Product and extra stock
 * - Server-side pricing
 * - Promo codes
 * - EFT bank-details snapshots
 * - Order retrieval
 * - Order status updates
 * - Inventory restoration
 *
 * Payment processing itself is handled separately from Order.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ExtraRepository extraRepository;
    private final ProductExtraRequirementRepository productExtraRequirementRepository;
    private final EmailService emailService;
    private final ProductService productService;
    private final BankDetailsService bankDetailsService;
    private final DailyOrderLimitService dailyOrderLimitService;
    private final PromoCodeService promoCodeService;

    private final ObjectMapper objectMapper;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ExtraRepository extraRepository,
            ProductExtraRequirementRepository productExtraRequirementRepository,
            EmailService emailService,
            ProductService productService,
            BankDetailsService bankDetailsService,
            DailyOrderLimitService dailyOrderLimitService,
            PromoCodeService promoCodeService,
            ObjectMapper objectMapper) {

        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.extraRepository = extraRepository;
        this.productExtraRequirementRepository = productExtraRequirementRepository;
        this.emailService = emailService;
        this.productService = productService;
        this.bankDetailsService = bankDetailsService;
        this.dailyOrderLimitService = dailyOrderLimitService;
        this.promoCodeService = promoCodeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new order.
     *
     * Payment method is supplied separately because payment information
     * belongs to the Payment entity, not the Order entity.
     *
     * @param order The Order object to create.
     * @param paymentMethod The selected payment method.
     * @return The created and saved Order.
     */
    @CacheEvict(value = "userOrders", allEntries = true)
        @Transactional
    public Order createOrder(Order order, PaymentMethod paymentMethod) {

        if (order == null) {
            throw new IllegalArgumentException("Order payload is required.");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required.");
        }

        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

                if (order.getUser() == null || order.getUser().getId() == null) {
                        throw new IllegalArgumentException("Order must have a valid user with ID.");
                }

                User customer = userRepository.findById(order.getUser().getId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Customer not found with ID: " + order.getUser().getId()
                                ));
                order.setUser(customer);

                String payloadHash = buildPayloadHash(order, paymentMethod);

                if (StringUtils.hasText(order.getIdempotencyKey())) {
                        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(
                                        customer.getId(), order.getIdempotencyKey()
                        );

                        if (existing.isPresent()) {
                                return resolveRetry(existing.get(), payloadHash);
                        }
                }

        // ---------------------------------------------------------
        // DAILY ORDER LIMIT
        // ---------------------------------------------------------

        Optional<DailyOrderLimit> limitSnapshot =
                dailyOrderLimitService.getOrderLimit();

        if (limitSnapshot.isPresent()
                && limitSnapshot.get().getLimitValue() <= 0) {

            throw new OrderLimitExceededException(
                    "We are sold out for today. Please try again tomorrow."
            );
        }

        Optional<DailyOrderLimit> limitOptional =
                dailyOrderLimitService.getOrderLimitForUpdate();

        int kotasInThisOrder = order.getOrderItems()
                .stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        if (limitOptional.isPresent()) {

            int limitValue = limitOptional.get().getLimitValue();
            int totalOrdered = getTodaysKotasOrdered();
            int remainingCapacity = limitValue - totalOrdered;

            log.info(
                    "[DailyLimit] limitValue={}, totalOrdered={}, remaining={}, thisOrder={}",
                    limitValue,
                    totalOrdered,
                    remainingCapacity,
                    kotasInThisOrder
            );

            if (remainingCapacity <= 0) {

                throw new OrderLimitExceededException(
                        "We are sold out for today. Please try again tomorrow."
                );
            }

            if (kotasInThisOrder > remainingCapacity) {

                throw new OrderLimitExceededException(
                        "Only " + remainingCapacity
                                + " kota left for today. Please reduce your quantity and try again."
                );
            }
        }

        // ---------------------------------------------------------
        // VALIDATE USER
        // ---------------------------------------------------------

        // ---------------------------------------------------------
        // INITIAL ORDER INFORMATION
        // ---------------------------------------------------------

        LocalDateTime orderDateTime = LocalDateTime.now();

        order.setOrderDate(orderDateTime);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPaymentMethod(paymentMethod);

        log.debug(
                "Initial orderDate set to {}",
                orderDateTime
        );

        // ---------------------------------------------------------
        // EFT BANK DETAILS
        // ---------------------------------------------------------
        //
        // Payment method now comes from the method parameter rather
        // than Order.paymentMethod.
        //
        // The bank details are still stored as a snapshot on Order
        // because the customer needs to see the EFT details that
        // were assigned to this particular order.
        // ---------------------------------------------------------

        if (paymentMethod == PaymentMethod.EFT) {

            if (order.getEftBankDetails() == null
                    || !order.getEftBankDetails().isValid()) {

                BankDetails assignedBankDetails =
                        bankDetailsService.getRandomEftBankDetails()
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "EFT payment selected, but bank details are unavailable."
                                        )
                                );

                order.setEftBankDetails(assignedBankDetails);
            }

            log.info(
                    "OrderService: EFT Bank Details selected for saving: {}",
                    order.getEftBankDetails()
            );
        }

        // ---------------------------------------------------------
        // PROCESS ORDER ITEMS AND VALIDATE STOCK
        // ---------------------------------------------------------

        Map<Long, Integer> extraDemandTotals = new HashMap<>();

        for (OrderItem item : order.getOrderItems()) {

            Product product = productRepository
                    .findById(item.getProduct().getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Product not found: "
                                            + item.getProduct().getId()
                            )
                    );

            if (item.getQuantity() == null || item.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Quantity for product "
                                + product.getName()
                                + " must be positive."
                );
            }

            item.setProduct(product);
            item.setPriceAtTimeOfOrder(product.getPrice());
            item.setOrder(order);

            // -----------------------------------------------------
            // REQUIRED EXTRAS
            // -----------------------------------------------------

            List<ProductExtraRequirement> requiredExtras =
                    productExtraRequirementRepository
                            .findByProductId(product.getId());

            for (ProductExtraRequirement requiredExtra : requiredExtras) {

                int unitsRequired =
                        requiredExtra.getUnitsRequired() == null
                                ? 0
                                : requiredExtra.getUnitsRequired();

                if (unitsRequired <= 0) {
                    continue;
                }

                int demand =
                        unitsRequired * item.getQuantity();

                extraDemandTotals.merge(
                        requiredExtra.getExtra().getId(),
                        demand,
                        Integer::sum
                );
            }

            // -----------------------------------------------------
            // SELECTED EXTRAS
            // -----------------------------------------------------

            if (StringUtils.hasText(item.getSelectedExtrasJson())) {

                Map<Long, Integer> selectedExtraDemand =
                        parseSelectedExtrasDemand(
                                item.getSelectedExtrasJson(),
                                item.getQuantity()
                        );

                selectedExtraDemand.forEach(
                        (extraId, demand) ->
                                extraDemandTotals.merge(
                                        extraId,
                                        demand,
                                        Integer::sum
                                )
                );
            }

            // -----------------------------------------------------
            // SELECTED SAUCES
            // -----------------------------------------------------

            if (StringUtils.hasText(item.getSelectedSaucesJson())) {

                parseSelectedSauces(
                        item.getSelectedSaucesJson()
                );
            }
        }

        // ---------------------------------------------------------
        // SERVER-SIDE PRICING
        // ---------------------------------------------------------

        double subtotal = order.getOrderItems()
                .stream()
                .mapToDouble(item ->
                        item.getPriceAtTimeOfOrder()
                                * item.getQuantity()
                )
                .sum();

        // Add extras cost.
        for (OrderItem item : order.getOrderItems()) {

            if (StringUtils.hasText(item.getSelectedExtrasJson())) {

                List<Map<String, Object>> extras =
                        readJsonValue(
                                item.getSelectedExtrasJson(),
                                new TypeReference<>() {},
                                "Invalid selected extras for product: "
                                        + item.getProduct().getName()
                        );

                for (Map<String, Object> extra : extras) {

                    Object priceVal = extra.get("price");
                    Object qtyVal = extra.get("quantity");

                    double extraPrice =
                            priceVal instanceof Number
                                    ? ((Number) priceVal).doubleValue()
                                    : 0.0;

                    int extraQty =
                            qtyVal instanceof Number
                                    ? ((Number) qtyVal).intValue()
                                    : 1;

                    subtotal +=
                            extraPrice
                                    * extraQty
                                    * item.getQuantity();
                }
            }
        }

        double deliveryFee =
                "DELIVERY".equalsIgnoreCase(order.getDeliveryMethod())
                        ? 5.0
                        : 0.0;

        double discountAmount = 0.0;

        // ---------------------------------------------------------
        // PROMO CODE
        // ---------------------------------------------------------

        if (StringUtils.hasText(order.getPromoCode())) {

            try {

                PromoCode promo =
                        promoCodeService.validatePromoCode(
                                order.getPromoCode(),
                                subtotal
                        );

                discountAmount =
                        promo.isPercentageDiscount()
                                ? subtotal
                                * (promo.getDiscountAmount() / 100.0)
                                : promo.getDiscountAmount();

                discountAmount =
                        Math.min(
                                discountAmount,
                                subtotal
                        );

            } catch (Exception e) {

                throw new IllegalArgumentException(
                        "Invalid promo code: "
                                + e.getMessage()
                );
            }
        }

        double totalAmount =
                Math.max(
                        0.0,
                        subtotal
                                + deliveryFee
                                - discountAmount
                );

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);

                Optional<Order> recentDuplicate = orderRepository.findRecentPotentialDuplicates(
                                customer.getId(),
                                LocalDateTime.now().minusMinutes(15),
                                totalAmount
                ).stream().filter(candidate -> sameOrderItems(candidate, order)).findFirst();

                if (recentDuplicate.isPresent()) {
                        return resolveRetry(recentDuplicate.get(), payloadHash);
                }

                for (OrderItem item : order.getOrderItems()) {
                        boolean stockDecreased;
                        try {
                                stockDecreased = productService.decreaseStock(
                                                item.getProduct().getId(), item.getQuantity()
                                );
                        } catch (PessimisticLockingFailureException ex) {
                                throw new ConcurrencyConflictException(
                                                "High traffic right now. Please try again.", ex
                                );
                        }
                        if (!stockDecreased) {
                                throw new InsufficientStockException(
                                                "Sorry, " + item.getProduct().getName()
                                                                + " is sold out. Please remove it or choose another item."
                                );
                        }
                }

                for (Map.Entry<Long, Integer> entry : extraDemandTotals.entrySet()) {
                        Long extraId = entry.getKey();
                        int demand = entry.getValue();
                        if (demand <= 0) {
                                continue;
                        }
                        boolean decremented;
                        try {
                                decremented = extraRepository.decrementStockIfAvailable(extraId, demand) == 1;
                        } catch (PessimisticLockingFailureException ex) {
                                throw new ConcurrencyConflictException(
                                                "High traffic right now. Please try again.", ex
                                );
                        }
                        if (!decremented) {
                                String extraName = extraRepository.findById(extraId)
                                                .map(Extra::getName).orElse("ID " + extraId);
                                throw new InsufficientStockException(
                                                "Sorry, " + extraName + " is sold out. Please remove it or choose another extra."
                                );
                        }
                }

                order.setPayloadHash(payloadHash);

        order.setOrderDate(LocalDateTime.now());

        // ---------------------------------------------------------
        // SAVE ORDER
        // ---------------------------------------------------------

        Order savedOrder =
                orderRepository.save(order);

        log.info(
                "OrderService: Saved order ID: {}, orderDate after save: {}",
                savedOrder.getId(),
                savedOrder.getOrderDate()
        );

        log.info(
                "OrderService: EFT Bank Details after saving: {}",
                savedOrder.getEftBankDetails()
        );

        order.getOrderItems()
                .forEach(item ->
                        item.setOrder(savedOrder)
                );

        orderItemRepository.saveAll(
                order.getOrderItems()
        );

        return savedOrder;
    }

    private Order resolveRetry(Order existing, String payloadHash) {
                String existingHash = existing.getPayloadHash();
                if (!StringUtils.hasText(existingHash)
                                && existing.getPayment() != null
                                && existing.getPayment().getPaymentMethod() != null) {
                        existingHash = buildPayloadHash(existing, existing.getPayment().getPaymentMethod());
                }

                if (payloadHash.equals(existingHash)) {
            log.info("Returning existing order {} for an idempotent retry", existing.getId());
            return existing;
        }

        throw new IdempotencyConflictException(
                "The idempotency key was already used with a different order request."
        );
    }

    private boolean sameOrderItems(Order left, Order right) {
        String leftHash = buildItemHash(left.getOrderItems());
        String rightHash = buildItemHash(right.getOrderItems());
        return leftHash.equals(rightHash)
                && normalize(left.getDeliveryMethod()).equals(normalize(right.getDeliveryMethod()))
                && normalize(left.getShippingAddress()).equals(normalize(right.getShippingAddress()))
                && normalize(left.getPromoCode()).equals(normalize(right.getPromoCode()))
                && java.util.Objects.equals(left.getScheduledDeliveryTime(), right.getScheduledDeliveryTime());
    }

    private String buildPayloadHash(Order order, PaymentMethod paymentMethod) {
        return sha256(order.getUser().getId() + "|" + paymentMethod + "|"
                + normalize(order.getShippingAddress()) + "|"
                + normalize(order.getDeliveryMethod()) + "|"
                + normalize(order.getPromoCode()) + "|"
                + order.getScheduledDeliveryTime() + "|"
                + buildItemHash(order.getOrderItems()));
    }

    private String buildItemHash(List<OrderItem> items) {
        StringBuilder payload = new StringBuilder();
        items.stream()
                .sorted((left, right) -> Long.compare(
                        left.getProduct().getId(), right.getProduct().getId()))
                .forEach(item -> payload
                        .append(item.getProduct().getId()).append(':')
                        .append(item.getQuantity()).append(':')
                        .append(normalize(item.getCustomizationNotes())).append(':')
                        .append(normalize(item.getSelectedExtrasJson())).append(':')
                        .append(normalize(item.getSelectedSaucesJson())).append('|'));
        return payload.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte current : digest) {
                hash.append(String.format("%02x", current));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Map<Long, Integer> parseSelectedExtrasDemand(
            String selectedExtrasJson,
            int orderItemQuantity) {

        if (!StringUtils.hasText(selectedExtrasJson)) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> extras =
                readJsonValue(
                        selectedExtrasJson,
                        new TypeReference<>() {},
                        "Invalid extras JSON payload."
                );

        Map<Long, Integer> demandByExtraId =
                new HashMap<>();

        for (Map<String, Object> extra : extras) {

            Long extraId =
                    resolveExtraId(extra);

            int unitsPerKota = 1;

            Object quantityValue =
                    extra.get("quantity");

            if (quantityValue instanceof Number quantityNumber) {

                unitsPerKota =
                        quantityNumber.intValue();
            }

            if (unitsPerKota <= 0) {

                throw new IllegalArgumentException(
                        "Selected extra quantity must be greater than zero."
                );
            }

            int totalDemand =
                    unitsPerKota * orderItemQuantity;

            demandByExtraId.merge(
                    extraId,
                    totalDemand,
                    Integer::sum
            );
        }

        return demandByExtraId;
    }

    private List<Sauce> parseSelectedSauces(
            String selectedSaucesJson) {

        return readJsonValue(
                selectedSaucesJson,
                new TypeReference<>() {},
                "Invalid selected sauces payload."
        );
    }

    private Long resolveExtraId(
            Map<String, Object> extra) {

        Object idValue = extra.get("id");

        if (idValue instanceof Number numberId) {

            return numberId.longValue();
        }

        Object nameValue = extra.get("name");

        if (nameValue instanceof String extraName
                && StringUtils.hasText(extraName)) {

            return extraRepository
                    .findByNameIgnoreCase(extraName.trim())
                    .map(Extra::getId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Selected extra not found: "
                                            + extraName
                            )
                    );
        }

        throw new IllegalArgumentException(
                "Each selected extra must include id or name."
        );
    }

    private <T> T readJsonValue(
            String json,
            TypeReference<T> typeReference,
            String errorMessage) {

        try {

            return objectMapper.readValue(
                    json,
                    typeReference
            );

        } catch (Exception ex) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }
    }

    /**
     * Helper method to send order confirmation emails.
     */
    @Transactional
    protected void sendOrderConfirmationEmails(Order order) {
        // Currently disabled.
    }

    /**
     * Retrieves all orders for a specific user.
     */
    public List<Order> getOrdersByUserId(Long userId) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found with ID: "
                                                + userId
                                )
                        );

        List<Order> orders =
                orderRepository.findByUser(user);

        orders.forEach(order -> {

            if (order.getOrderItems() != null) {

                order.getOrderItems().size();

                order.getOrderItems().forEach(orderItem -> {

                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName();
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedExtrasJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedExtrasJson(),
                                    new TypeReference<List<Extra>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedSaucesJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedSaucesJson(),
                                    new TypeReference<List<Sauce>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            log.debug(
                    "OrderService: Retrieved Order ID {}. EFT Bank Details: {}",
                    order.getId(),
                    order.getEftBankDetails()
            );
        });

        return orders;
    }

    /**
     * Optimized version that retrieves all orders for a specific user.
     */
    @Cacheable(value = "userOrders", key = "#userId")
    public List<Order> getOrdersByUserIdOptimized(Long userId) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found with ID: "
                                                + userId
                                )
                        );

        List<Order> orders =
                orderRepository.findByUserWithOrderItemsAndProducts(user);

        orders.forEach(order -> {

            if (order.getOrderItems() != null) {

                order.getOrderItems().forEach(orderItem -> {

                    if (StringUtils.hasText(
                            orderItem.getSelectedExtrasJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedExtrasJson(),
                                    new TypeReference<List<Extra>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedSaucesJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedSaucesJson(),
                                    new TypeReference<List<Sauce>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            log.debug(
                    "OrderService: Retrieved (Optimized) Order ID {}. EFT Bank Details: {}",
                    order.getId(),
                    order.getEftBankDetails()
            );
        });

        return orders;
    }

    /**
     * Retrieves all orders in the system.
     */
    public List<Order> getAllOrders() {

        List<Order> orders =
                orderRepository.findAll();

        orders.forEach(order -> {

            if (order.getUser() != null) {

                order.getUser().getFirstName();
                order.getUser().getLastName();
                order.getUser().getEmail();
            }

            if (order.getOrderItems() != null) {

                order.getOrderItems().size();

                order.getOrderItems().forEach(orderItem -> {

                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName();
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedExtrasJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedExtrasJson(),
                                    new TypeReference<List<Extra>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedSaucesJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedSaucesJson(),
                                    new TypeReference<List<Sauce>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            log.debug(
                    "OrderService: Retrieved (All) Order ID {}. EFT Bank Details: {}",
                    order.getId(),
                    order.getEftBankDetails()
            );
        });

        return orders;
    }

    /**
     * Retrieves a single order by its ID.
     */
    public Optional<Order> getOrderById(Long orderId) {

        Optional<Order> orderOptional =
                orderRepository.findById(orderId);

        orderOptional.ifPresent(order -> {

            if (order.getUser() != null) {

                order.getUser().getFirstName();
                order.getUser().getLastName();
                order.getUser().getEmail();
            }

            if (order.getOrderItems() != null) {

                order.getOrderItems().size();

                order.getOrderItems().forEach(orderItem -> {

                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName();
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedExtrasJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedExtrasJson(),
                                    new TypeReference<List<Extra>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    if (StringUtils.hasText(
                            orderItem.getSelectedSaucesJson())) {

                        try {

                            objectMapper.readValue(
                                    orderItem.getSelectedSaucesJson(),
                                    new TypeReference<List<Sauce>>() {}
                            );

                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            log.debug(
                    "OrderService: Retrieved (Single) Order ID {}. EFT Bank Details: {}",
                    order.getId(),
                    order.getEftBankDetails()
            );
        });

        return orderOptional;
    }

    /**
     * Updates the status of an order.
     */
    @CacheEvict(value = "userOrders", allEntries = true)
    public Optional<Order> updateOrderStatus(
            Long orderId,
            Order.OrderStatus newStatus) {

        return orderRepository.findById(orderId)
                .map(order -> {

                    Order.OrderStatus currentStatus =
                            order.getStatus();

                    if (currentStatus == Order.OrderStatus.CANCELLED
                            && newStatus != Order.OrderStatus.CANCELLED) {

                        throw new IllegalArgumentException(
                                "Cancelled orders cannot be reactivated."
                        );
                    }

                    if (newStatus == Order.OrderStatus.CANCELLED
                            && currentStatus != Order.OrderStatus.CANCELLED) {

                        restoreInventoryForOrder(order);
                    }

                    order.setStatus(newStatus);

                    Order saved =
                            orderRepository.save(order);

                    if (newStatus == Order.OrderStatus.READY
                            && saved.getUser() != null) {

                        try {

                            emailService.sendOrderReadyEmail(
                                    saved.getUser().getEmail(),
                                    saved.getUser().getFirstName(),
                                    saved.getId()
                            );

                        } catch (Exception e) {

                            log.error(
                                    "Failed to send order ready email for order {}: {}",
                                    saved.getId(),
                                    e.getMessage()
                            );
                        }
                    }

                    if (saved.getUser() != null) {

                        saved.getUser().getFirstName();
                        saved.getUser().getLastName();
                        saved.getUser().getEmail();
                    }

                    if (saved.getOrderItems() != null) {

                        saved.getOrderItems().size();

                        saved.getOrderItems().forEach(orderItem -> {

                            if (orderItem.getProduct() != null) {
                                orderItem.getProduct().getName();
                            }

                            if (StringUtils.hasText(
                                    orderItem.getSelectedExtrasJson())) {

                                try {

                                    objectMapper.readValue(
                                            orderItem.getSelectedExtrasJson(),
                                            new TypeReference<List<Extra>>() {}
                                    );

                                } catch (Exception ignored) {
                                }
                            }

                            if (StringUtils.hasText(
                                    orderItem.getSelectedSaucesJson())) {

                                try {

                                    objectMapper.readValue(
                                            orderItem.getSelectedSaucesJson(),
                                            new TypeReference<List<Sauce>>() {}
                                    );

                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }

                    return saved;
                });
    }

    /**
     * Deletes an order by its ID and restores inventory.
     */
    @CacheEvict(value = "userOrders", allEntries = true)
    public boolean deleteOrder(Long id) {

        Optional<Order> orderOptional =
                orderRepository.findById(id);

        if (orderOptional.isPresent()) {

            Order order = orderOptional.get();

            if (order.getStatus() != Order.OrderStatus.CANCELLED) {
                restoreInventoryForOrder(order);
            }

            orderRepository.delete(order);

            return true;
        }

        return false;
    }

    private void restoreInventoryForOrder(Order order) {

        if (order.getOrderItems() == null
                || order.getOrderItems().isEmpty()) {

            return;
        }

        Map<Long, Integer> extraRestores =
                new HashMap<>();

        for (OrderItem item : order.getOrderItems()) {

            if (item.getProduct() == null
                    || item.getProduct().getId() == null) {

                throw new IllegalArgumentException(
                        "Order item is missing product information."
                );
            }

            if (item.getQuantity() == null
                    || item.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Order item quantity must be positive."
                );
            }

            boolean restored =
                    productService.increaseStock(
                            item.getProduct().getId(),
                            item.getQuantity()
                    );

            if (!restored) {

                throw new IllegalStateException(
                        "Failed to restore stock for product ID: "
                                + item.getProduct().getId()
                );
            }

            List<ProductExtraRequirement> requiredExtras =
                    productExtraRequirementRepository
                            .findByProductId(
                                    item.getProduct().getId()
                            );

            for (ProductExtraRequirement requiredExtra
                    : requiredExtras) {

                int unitsRequired =
                        requiredExtra.getUnitsRequired() == null
                                ? 0
                                : requiredExtra.getUnitsRequired();

                if (unitsRequired <= 0) {
                    continue;
                }

                int demand =
                        unitsRequired * item.getQuantity();

                extraRestores.merge(
                        requiredExtra.getExtra().getId(),
                        demand,
                        Integer::sum
                );
            }

            if (StringUtils.hasText(
                    item.getSelectedExtrasJson())) {

                Map<Long, Integer> selectedExtraDemand =
                        parseSelectedExtrasDemand(
                                item.getSelectedExtrasJson(),
                                item.getQuantity()
                        );

                selectedExtraDemand.forEach(
                        (extraId, demand) ->
                                extraRestores.merge(
                                        extraId,
                                        demand,
                                        Integer::sum
                                )
                );
            }
        }

        for (Map.Entry<Long, Integer> entry
                : extraRestores.entrySet()) {

            Long extraId = entry.getKey();
            int quantity = entry.getValue();

            if (quantity <= 0) {
                continue;
            }

            int restored =
                    extraRepository.incrementStock(
                            extraId,
                            quantity
                    );

            if (restored != 1) {

                throw new IllegalStateException(
                        "Failed to restore stock for extra ID: "
                                + extraId
                );
            }
        }
    }

    /**
     * Retrieves the total count of all orders.
     */
    public long getTotalOrderCount() {
        return orderRepository.count();
    }

    /**
     * Counts the total number of kotas ordered across all time.
     */
    public int getAllTimeKotasOrdered() {

        Long total =
                orderRepository.sumAllKotasOrdered();

        int result =
                (total == null)
                        ? 0
                        : total.intValue();

        log.debug(
                "[DailyLimit] Total kotas ordered all-time: {}",
                result
        );

        return result;
    }

    /**
     * Counts the total number of kotas ordered today.
     */
    public int getTodaysKotasOrdered() {

        LocalDateTime startOfDay =
                LocalDateTime.now()
                        .toLocalDate()
                        .atStartOfDay();

        LocalDateTime endOfDay =
                startOfDay.plusDays(1);

        int todaysTotal =
                orderRepository.sumKotasOrderedBetween(
                        startOfDay,
                        endOfDay
                );

        log.debug(
                "[DailyLimit] Total kotas ordered today ({} to {}): {}",
                startOfDay,
                endOfDay,
                todaysTotal
        );

        return todaysTotal;
    }
}