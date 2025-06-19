// src/main/java/kasiKotas/service/OrderService.java
package kasiKotas.service;

import kasiKotas.model.*;
import kasiKotas.repository.OrderRepository;
import kasiKotas.repository.OrderItemRepository;
import kasiKotas.repository.UserRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.DailyOrderLimitRepository;
import kasiKotas.service.EmailService;
import kasiKotas.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.MessagingException;

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

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EmailService emailService;
    private final ProductService productService;
    private final DailyOrderLimitRepository dailyOrderLimitRepository;

    private final ObjectMapper objectMapper;

    @Value("${admin.email}")
    private String adminEmail;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            EmailService emailService,
            ProductService productService,
            DailyOrderLimitRepository dailyOrderLimitRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.emailService = emailService;
        this.productService = productService;
        this.dailyOrderLimitRepository = dailyOrderLimitRepository;
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
        // 1. Check Daily Order Limit
        List<DailyOrderLimit> limits = dailyOrderLimitRepository.findAll();
        if (!limits.isEmpty()) {
            DailyOrderLimit currentLimit = limits.get(0);
            long totalOrdersCount = orderRepository.count();
            if (currentLimit.getLimitValue() > 0 && totalOrdersCount >= currentLimit.getLimitValue()) {
                throw new IllegalArgumentException("Order limit reached. We are currently not taking new orders.");
            }
        }

        // 2. Validate User
        User customer = userRepository.findById(order.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        order.setUser(customer);

        order.setOrderDate(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.PENDING);
        // Removed: order.setDiscountAmount(0.0); as per user request to remove discount logic

        double totalAmount = 0.0;

        // 3. Process Order Items and Calculate Total
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProduct().getId()));

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity for product " + product.getName() + " must be positive.");
            }

            // Check stock and decrease
            Optional<Product> updatedProductOptional = productService.decreaseStock(product.getId(), item.getQuantity());
            if (updatedProductOptional.isEmpty()) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
            }

            item.setProduct(product);
            item.setPriceAtTimeOfOrder(product.getPrice());
            item.setOrder(order);

            double itemTotal = item.getQuantity() * item.getPriceAtTimeOfOrder();

            if (StringUtils.hasText(item.getSelectedExtrasJson())) {
                try {
                    List<Extra> selectedExtras = objectMapper.readValue(item.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {});
                    for (Extra extra : selectedExtras) {
                        itemTotal += (extra.getPrice() * item.getQuantity());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse selectedExtrasJson for order item " + item.getProduct().getName() + ": " + e.getMessage());
                }
            }

            if (StringUtils.hasText(item.getSelectedSaucesJson())) {
                try {
                    objectMapper.readValue(item.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {});
                } catch (Exception e) {
                    System.err.println("Failed to parse selectedSaucesJson for order item " + item.getProduct().getName() + ": " + e.getMessage());
                }
            }

            totalAmount += itemTotal;
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        order.getOrderItems().forEach(item -> item.setOrder(savedOrder));
        orderItemRepository.saveAll(order.getOrderItems());

        // Commented out PDF generation and email sending as per request
        // sendOrderConfirmationEmails(savedOrder);

        return savedOrder;
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
                    order.setStatus(newStatus); // Use the directly provided enum
                    return orderRepository.save(order);
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
            order.getOrderItems().forEach(orderItem -> {
                productService.decreaseStock(orderItem.getProduct().getId(), -orderItem.getQuantity());
            });

            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Retrieves the total count of all orders.
     * @return The total number of orders.
     */
    public long getTotalOrderCount() {
        return orderRepository.count();
    }
}
