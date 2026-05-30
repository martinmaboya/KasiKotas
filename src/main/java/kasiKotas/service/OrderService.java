// src/main/java/kasiKotas/service/OrderService.java
package kasiKotas.service;

import kasiKotas.exception.ConcurrencyConflictException;
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
// import kasiKotas.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service layer for managing Order related business logic.
 * This class handles creating orders, updating order status, and retrieving orders.
 * It integrates with ProductService to update stock and EmailService for notifications.
 *
 * This version includes:
 * - Daily order limit checks.
 * - Proper handling for customization notes, selected extras, AND selected sauces.
 * - Comprehensive initialization of lazy-loaded entities for JSON serialization and PDF generation.
 * - Email notifications with order details and PDFs to both customer and admin.
 * - Promo code functionality has been entirely removed as per previous user request.
 */
@Service
@Transactional // Ensures all methods in this service are transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ExtraRepository extraRepository;
    private final ProductExtraRequirementRepository productExtraRequirementRepository;
    // private final EmailService emailService;
    private final ProductService productService;
    private final BankDetailsService bankDetailsService; // Keep this for other potential uses, but not for setting EFT details in createOrder
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
            // EmailService emailService,
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
        this.productService = productService;
        this.bankDetailsService = bankDetailsService;
        this.dailyOrderLimitService = dailyOrderLimitService;
        this.promoCodeService = promoCodeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new order.
     * This method handles the entire order creation process, including:
     * - Validating user and product existence.
     * - Checking against the total order limit.
     * - Calculating total amount (including extras).
     * - Decreasing product stock.
     * - Saving the order and its items (including customization notes, extras, and sauces).
     * - Sending email confirmations to customer and admin.
     *
     * @param order The Order object to create, including its associated OrderItems and payment method.
     * @return The created and saved Order object.
     * @throws IllegalArgumentException if validation fails (e.g., user not found, insufficient stock, limit reached).
     */
    public Order createOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order payload is required.");
        }

        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        // 1. Check daily order limit while holding a pessimistic lock.
        Optional<DailyOrderLimit> limitOptional = dailyOrderLimitService.getOrderLimitForUpdate();

        // Calculate kotas in this new order
        int kotasInThisOrder = order.getOrderItems().stream()
            .mapToInt(OrderItem::getQuantity)
            .sum();

        if (limitOptional.isPresent()) {
            int limitValue = limitOptional.get().getLimitValue();
            int totalOrdered = getTodaysKotasOrdered();
            int remainingCapacity = limitValue - totalOrdered;

            log.info("[DailyLimit] limitValue={}, totalOrdered={}, remaining={}, thisOrder={}",
                    limitValue, totalOrdered, remainingCapacity, kotasInThisOrder);

            if (remainingCapacity <= 0) {
                throw new OrderLimitExceededException(
                        "Kota limit reached for today. Please try again tomorrow.");
            }
            if (kotasInThisOrder > remainingCapacity) {
                throw new OrderLimitExceededException(
                        "Kota limit reached! You can only order " + remainingCapacity +
                                " more kota today. Please reduce your order quantity and try again.");
            }
        }

        // 2. Validate User
        if (order.getUser() == null || order.getUser().getId() == null) {
            throw new IllegalArgumentException("Order must have a valid user with ID.");
        }
        User customer = userRepository.findById(order.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + order.getUser().getId()));
        order.setUser(customer);

        // Set orderDate and status early
        LocalDateTime orderDateTime = LocalDateTime.now();
        order.setOrderDate(orderDateTime);
        order.setStatus(Order.OrderStatus.PENDING);
        log.debug("Initial orderDate set to {}", orderDateTime);

        // If EFT is the payment method, assign a current bank-details snapshot on the server.
        // Never depend on the frontend to supply bank details for order creation.
        if ("eft".equalsIgnoreCase(order.getPaymentMethod())) {
            if (order.getEftBankDetails() == null || !order.getEftBankDetails().isValid()) {
                BankDetails assignedBankDetails = bankDetailsService.getRandomEftBankDetails()
                        .orElseThrow(() -> new IllegalArgumentException("EFT payment selected, but bank details are unavailable."));
                order.setEftBankDetails(assignedBankDetails);
            }
            log.info("OrderService: EFT Bank Details selected for saving: {}", order.getEftBankDetails());
        }

        // 3. Process order items and validate stock
        // Stock is decremented atomically per item to prevent concurrent overselling.
        Map<Long, Integer> extraDemandTotals = new HashMap<>();
        for (OrderItem item : order.getOrderItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProduct().getId()));

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity for product " + product.getName() + " must be positive.");
            }

            // Atomic check-and-decrement at DB level: succeeds only if stock >= requested quantity now.
            boolean stockDecreased;
            try {
                stockDecreased = productService.decreaseStock(product.getId(), item.getQuantity());
            } catch (PessimisticLockingFailureException ex) {
                throw new ConcurrencyConflictException("High traffic right now. Please try again.", ex);
            }
            if (!stockDecreased) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
            }

            item.setProduct(product);
            item.setPriceAtTimeOfOrder(product.getPrice());
            item.setOrder(order);

            // Every kota can consume required extras even when user does not explicitly select them.
            List<ProductExtraRequirement> requiredExtras = productExtraRequirementRepository.findByProductId(product.getId());
            for (ProductExtraRequirement requiredExtra : requiredExtras) {
                int unitsRequired = requiredExtra.getUnitsRequired() == null ? 0 : requiredExtra.getUnitsRequired();
                if (unitsRequired <= 0) {
                    continue;
                }
                int demand = unitsRequired * item.getQuantity();
                extraDemandTotals.merge(requiredExtra.getExtra().getId(), demand, Integer::sum);
            }

            // Validate extras and sauces JSON (but don't recalculate total)
            if (StringUtils.hasText(item.getSelectedExtrasJson())) {
                Map<Long, Integer> selectedExtraDemand = parseSelectedExtrasDemand(item.getSelectedExtrasJson(), item.getQuantity());
                selectedExtraDemand.forEach((extraId, demand) -> extraDemandTotals.merge(extraId, demand, Integer::sum));
            }

            if (StringUtils.hasText(item.getSelectedSaucesJson())) {
                parseSelectedSauces(item.getSelectedSaucesJson());
            }
        }

        // Decrement extras after all items are validated; transaction rollback guarantees consistency.
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
                throw new ConcurrencyConflictException("High traffic right now. Please try again.", ex);
            }
            if (!decremented) {
                String extraName = extraRepository.findById(extraId).map(Extra::getName).orElse("ID " + extraId);
                throw new InsufficientStockException("Insufficient stock for extra: " + extraName);
            }
        }

        // 4. Recalculate pricing server-side — never trust the frontend for financial data
        double subtotal = order.getOrderItems().stream()
                .mapToDouble(item -> item.getPriceAtTimeOfOrder() * item.getQuantity())
                .sum();

        // Add extras cost
        for (OrderItem item : order.getOrderItems()) {
            if (StringUtils.hasText(item.getSelectedExtrasJson())) {
                List<Map<String, Object>> extras = readJsonValue(
                        item.getSelectedExtrasJson(),
                        new TypeReference<>() {},
                        "Invalid selected extras for product: " + item.getProduct().getName());
                for (Map<String, Object> extra : extras) {
                    Object priceVal = extra.get("price");
                    Object qtyVal = extra.get("quantity");
                    double extraPrice = priceVal instanceof Number ? ((Number) priceVal).doubleValue() : 0.0;
                    int extraQty = qtyVal instanceof Number ? ((Number) qtyVal).intValue() : 1;
                    subtotal += extraPrice * extraQty * item.getQuantity();
                }
            }
        }

        double deliveryFee = "DELIVERY".equalsIgnoreCase(order.getDeliveryMethod()) ? 5.0 : 0.0;
        double discountAmount = 0.0;

        if (StringUtils.hasText(order.getPromoCode())) {
            try {
                PromoCode promo = promoCodeService.validatePromoCode(order.getPromoCode(), subtotal);
                discountAmount = promo.isPercentageDiscount()
                        ? subtotal * (promo.getDiscountAmount() / 100.0)
                        : promo.getDiscountAmount();
                discountAmount = Math.min(discountAmount, subtotal);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid promo code: " + e.getMessage());
            }
        }

        double totalAmount = Math.max(0.0, subtotal + deliveryFee - discountAmount);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);

        order.setOrderDate(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("OrderService: Saved order ID: {}, orderDate after save: {}", savedOrder.getId(), savedOrder.getOrderDate());
        log.info("OrderService: EFT Bank Details after saving: {}", savedOrder.getEftBankDetails());

        order.getOrderItems().forEach(item -> item.setOrder(savedOrder));
        orderItemRepository.saveAll(order.getOrderItems());

        // Remaining capacity is computed dynamically (limitValue - totalOrdered),
        // so no manual decrement needed here.

        return savedOrder;
    }

    private Map<Long, Integer> parseSelectedExtrasDemand(String selectedExtrasJson, int orderItemQuantity) {
        if (!StringUtils.hasText(selectedExtrasJson)) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> extras = readJsonValue(
                selectedExtrasJson,
                new TypeReference<>() {},
                "Invalid extras JSON payload."
        );

        Map<Long, Integer> demandByExtraId = new HashMap<>();
        for (Map<String, Object> extra : extras) {
            Long extraId = resolveExtraId(extra);

            int unitsPerKota = 1;
            Object quantityValue = extra.get("quantity");
            if (quantityValue instanceof Number quantityNumber) {
                unitsPerKota = quantityNumber.intValue();
            }
            if (unitsPerKota <= 0) {
                throw new IllegalArgumentException("Selected extra quantity must be greater than zero.");
            }

            int totalDemand = unitsPerKota * orderItemQuantity;
            demandByExtraId.merge(extraId, totalDemand, Integer::sum);
        }
        return demandByExtraId;
    }

    private List<Sauce> parseSelectedSauces(String selectedSaucesJson) {
        return readJsonValue(
                selectedSaucesJson,
                new TypeReference<>() {},
                "Invalid selected sauces payload."
        );
    }

    private Long resolveExtraId(Map<String, Object> extra) {
        Object idValue = extra.get("id");
        if (idValue instanceof Number numberId) {
            return numberId.longValue();
        }

        Object nameValue = extra.get("name");
        if (nameValue instanceof String extraName && StringUtils.hasText(extraName)) {
            return extraRepository.findByNameIgnoreCase(extraName.trim())
                    .map(Extra::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected extra not found: " + extraName));
        }

        throw new IllegalArgumentException("Each selected extra must include id or name.");
    }

    private <T> T readJsonValue(String json, TypeReference<T> typeReference, String errorMessage) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
    /**
     * Helper method to send order confirmation emails.
     * This is called asynchronously to not block the main transaction.
     * @param order The saved Order object.
     */
    @Transactional
    protected void sendOrderConfirmationEmails(Order order) {
        // All code inside this method has been commented out as per user request.
        /*
        try {
            User customer = userRepository.findById(order.getUser().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found for email sending."));

            List<OrderItem> fullOrderItems = orderItemRepository.findByOrder(order);
            fullOrderItems.forEach(item -> {
                if (item.getProduct() != null) {
                    item.getProduct().getName();
                }
            });
            order.setOrderItems(fullOrderItems);

            byte[] customerPdf = emailService.generateCustomerOrderPdf(order, customer);
            emailService.sendEmailWithAttachment(
                    customer.getEmail(),
                    "KasiKotas Order Confirmation #" + order.getId(),
                    "Your KasiKotas order has been successfully placed. Find your order details attached.",
                    customerPdf,
                    "Order_" + order.getId() + ".pdf"
            );

            byte[] adminPdf = emailService.generateAdminOrderNotificationPdf(order);
            emailService.sendEmailWithAttachment(
                    adminEmail,
                    "NEW KasiKotas Order Placed: #" + order.getId(),
                    "A new order has been placed on KasiKotas. Please find the details attached.",
                    adminPdf,
                    "Admin_Order_" + order.getId() + ".pdf"
            );
        } catch (MessagingException e) {
            System.err.println("Failed to send order confirmation email (MessagingException): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred during PDF generation or email sending: " + e.getMessage());
            e.printStackTrace();
        }
        */
    }


    /**
     * Retrieves all orders for a specific user.
     * This method explicitly initializes lazy relationships to prevent LazyInitializationException
     * when entities are later serialized outside the transactional context.
     * @param userId The ID of the user.
     * @return A list of orders placed by the user.
     * @throws IllegalArgumentException if user not found.
     */
    public List<Order> getOrdersByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        List<Order> orders = orderRepository.findByUser(user);

        orders.forEach(order -> {
            if (order.getOrderItems() != null) {
                order.getOrderItems().size();
                order.getOrderItems().forEach(orderItem -> {
                    if (orderItem.getProduct() != null) {
                        orderItem.getProduct().getName();
                    }
                    if (StringUtils.hasText(orderItem.getSelectedExtrasJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {}); } catch (Exception ignored) {}
                    }
                    if (StringUtils.hasText(orderItem.getSelectedSaucesJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {}); } catch (Exception ignored) {}
                    }
                });
            }
            log.debug("OrderService: Retrieved Order ID {}. EFT Bank Details: {}", order.getId(), order.getEftBankDetails());
        });
        return orders;
    }

    /**
     * Optimized version that retrieves all orders for a specific user.
     * Uses a single query with JOIN FETCH to avoid N+1 query problems.
     * This significantly improves performance for users with many orders.
     * @param userId The ID of the user.
     * @return A list of orders placed by the user.
     * @throws IllegalArgumentException if user not found.
     */
    public List<Order> getOrdersByUserIdOptimized(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Use optimized query that fetches all related data in one query
        List<Order> orders = orderRepository.findByUserWithOrderItemsAndProducts(user);
        
        // Still need to process JSON fields for extras and sauces since they can't be fetched with JOIN
        orders.forEach(order -> {
            if (order.getOrderItems() != null) {
                order.getOrderItems().forEach(orderItem -> {
                    if (StringUtils.hasText(orderItem.getSelectedExtrasJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {}); } catch (Exception ignored) {}
                    }
                    if (StringUtils.hasText(orderItem.getSelectedSaucesJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {}); } catch (Exception ignored) {}
                    }
                });
            }
            log.debug("OrderService: Retrieved (Optimized) Order ID {}. EFT Bank Details: {}", order.getId(), order.getEftBankDetails());
        });
        return orders;
    }

    /**
     * Retrieves all orders in the system (typically for admin view).
     * This method explicitly initializes lazy relationships to prevent LazyInitializationException
     * when entities are later serialized outside the transactional context.
     * @return A list of all Order objects.
     */
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
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
                    if (StringUtils.hasText(orderItem.getSelectedExtrasJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {}); } catch (Exception ignored) {}
                    }
                    if (StringUtils.hasText(orderItem.getSelectedSaucesJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {}); } catch (Exception ignored) {}
                    }
                });
            }
            log.debug("OrderService: Retrieved (All) Order ID {}. EFT Bank Details: {}", order.getId(), order.getEftBankDetails());
        });
        return orders;
    }

    /**
     * Retrieves a single order by its ID.
     * This method explicitly initializes lazy relationships to prevent LazyInitializationException
     * when the entity is later serialized outside the transactional context.
     * @param orderId The ID of the order to retrieve.
     * @return An Optional containing the Order if found, or empty if not found.
     */
    public Optional<Order> getOrderById(Long orderId) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);
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
                    if (StringUtils.hasText(orderItem.getSelectedExtrasJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {}); } catch (Exception ignored) {}
                    }
                    if (StringUtils.hasText(orderItem.getSelectedSaucesJson())) {
                        try { objectMapper.readValue(orderItem.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {}); } catch (Exception ignored) {}
                    }
                });
            }
            log.debug("OrderService: Retrieved (Single) Order ID {}. EFT Bank Details: {}", order.getId(), order.getEftBankDetails());
        });
        return orderOptional;
    }

    /**
     * Updates the status of an order.
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return An Optional containing the updated Order if found, or empty if not found.
     * @throws IllegalArgumentException if the new status is invalid.
     */
    public Optional<Order> updateOrderStatus(Long orderId, Order.OrderStatus newStatus) { // Changed parameter type to Order.OrderStatus
        return orderRepository.findById(orderId)
                .map(order -> {
                    Order.OrderStatus currentStatus = order.getStatus();

                    if (currentStatus == Order.OrderStatus.CANCELLED && newStatus != Order.OrderStatus.CANCELLED) {
                        throw new IllegalArgumentException("Cancelled orders cannot be reactivated.");
                    }

                    if (newStatus == Order.OrderStatus.CANCELLED && currentStatus != Order.OrderStatus.CANCELLED) {
                        restoreInventoryForOrder(order);
                    }

                    order.setStatus(newStatus); // Use the directly provided enum
                    Order saved = orderRepository.save(order);

                    // Initialize lazy relations before returning so serialization doesn't fail after tx closes.
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
                            if (StringUtils.hasText(orderItem.getSelectedExtrasJson())) {
                                try {
                                    objectMapper.readValue(orderItem.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {});
                                } catch (Exception ignored) {
                                }
                            }
                            if (StringUtils.hasText(orderItem.getSelectedSaucesJson())) {
                                try {
                                    objectMapper.readValue(orderItem.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {});
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }

                    return saved;
                });
    }

    /**
     * Deletes an order by its ID.
     * Also restores the stock of products associated with the deleted order.
     * @param id The ID of the order to delete.
     * @return true if the order was found and deleted, false otherwise.
     */
    public boolean deleteOrder(Long id) {
        Optional<Order> orderOptional = orderRepository.findById(id);
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
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }

        Map<Long, Integer> extraRestores = new HashMap<>();
        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct() == null || item.getProduct().getId() == null) {
                throw new IllegalArgumentException("Order item is missing product information.");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Order item quantity must be positive.");
            }

            boolean restored = productService.increaseStock(item.getProduct().getId(), item.getQuantity());
            if (!restored) {
                throw new IllegalStateException("Failed to restore stock for product ID: " + item.getProduct().getId());
            }

            List<ProductExtraRequirement> requiredExtras = productExtraRequirementRepository.findByProductId(item.getProduct().getId());
            for (ProductExtraRequirement requiredExtra : requiredExtras) {
                int unitsRequired = requiredExtra.getUnitsRequired() == null ? 0 : requiredExtra.getUnitsRequired();
                if (unitsRequired <= 0) {
                    continue;
                }
                int demand = unitsRequired * item.getQuantity();
                extraRestores.merge(requiredExtra.getExtra().getId(), demand, Integer::sum);
            }

            if (StringUtils.hasText(item.getSelectedExtrasJson())) {
                Map<Long, Integer> selectedExtraDemand = parseSelectedExtrasDemand(item.getSelectedExtrasJson(), item.getQuantity());
                selectedExtraDemand.forEach((extraId, demand) -> extraRestores.merge(extraId, demand, Integer::sum));
            }
        }

        for (Map.Entry<Long, Integer> entry : extraRestores.entrySet()) {
            Long extraId = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }

            int restored = extraRepository.incrementStock(extraId, quantity);
            if (restored != 1) {
                throw new IllegalStateException("Failed to restore stock for extra ID: " + extraId);
            }
        }
    }

    /**
     * Retrieves the total count of all orders.
     * @return The total number of orders.
     */
    public long getTotalOrderCount() {
        return orderRepository.count();
    }

    /**
     * Counts the total number of kotas ordered across all time.
     * @return The total number of kotas (order items) ever ordered.
     */
    public int getAllTimeKotasOrdered() {
        Long total = orderRepository.sumAllKotasOrdered();
        int result = (total == null) ? 0 : total.intValue();
        log.debug("[DailyLimit] Total kotas ordered all-time: {}", result);
        return result;
    }

    /**
     * Counts the total number of kotas ordered today.
     * This is used for display purposes to show admins how many kotas have been ordered today.
     * Calculates kotas from midnight to now on the current date.
     * @return The total number of kotas (order items) ordered today.
     */
    public int getTodaysKotasOrdered() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        int todaysTotal = orderRepository.sumKotasOrderedBetween(startOfDay, endOfDay);

        log.debug("[DailyLimit] Total kotas ordered today ({} to {}): {}", startOfDay, endOfDay, todaysTotal);
        return todaysTotal;
    }
}
