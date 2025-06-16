// src/main/java/kasiKotas/service/OrderService.java
package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.model.OrderItem;
import kasiKotas.model.Product;
import kasiKotas.model.User;
import kasiKotas.repository.OrderRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Import for @Value
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // For String utility methods

import jakarta.mail.MessagingException; // Import MessagingException
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

/**
 * Service layer for managing Order related business logic.
 * Handles creation, retrieval, and updates of orders.
 * Now includes a method to count ALL orders for the total limit feature.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final EmailService emailService; // Inject EmailService

    // FIX: Correctly reference the property key 'admin.email' from application.properties
    @Value("${admin.email}")
    private String adminEmail;

    @Autowired
    public OrderService(OrderRepository orderRepository, UserRepository userRepository, ProductService productService, EmailService emailService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.emailService = emailService;
    }

    /**
     * Creates a new order.
     * Validates product stock and decrements it.
     * @param userId The ID of the user placing the order.
     * @param orderItemsRequest A list of OrderItem objects containing product IDs and quantities.
     * @param shippingAddress The shipping address for the order.
     * @return The created Order object.
     * @throws IllegalArgumentException if user not found, product not found, or insufficient stock.
     */
    public Order createOrder(Long userId, List<OrderItem> orderItemsRequest, String shippingAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with ID " + userId + " not found."));

        if (orderItemsRequest == null || orderItemsRequest.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        if (!StringUtils.hasText(shippingAddress)) {
            throw new IllegalArgumentException("Shipping address cannot be empty.");
        }

        Order order = new Order();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(Order.OrderStatus.PENDING); // Initial status: PENDING
        order.setShippingAddress(shippingAddress);

        double totalAmount = 0.0;
        List<OrderItem> actualOrderItems = new java.util.ArrayList<>();

        // Process each item in the order request
        for (OrderItem itemRequest : orderItemsRequest) {
            if (itemRequest.getProduct() == null || itemRequest.getProduct().getId() == null) {
                throw new IllegalArgumentException("Product ID cannot be null for an order item.");
            }
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity for product ID " + itemRequest.getProduct().getId() + " must be positive.");
            }

            // Retrieve the product and check stock
            Product product = productService.getProductById(itemRequest.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product with ID " + itemRequest.getProduct().getId() + " not found."));

            if (product.getStock() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getName() +
                        ". Available: " + product.getStock() + ", Requested: " + itemRequest.getQuantity());
            }

            // Decrease product stock. This is handled by the ProductService.
            productService.decreaseStock(product.getId(), itemRequest.getQuantity())
                    .orElseThrow(() -> new IllegalStateException("Failed to decrease stock for product " + product.getId()));


            // Create OrderItem for the order
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order); // Link to the current order
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPriceAtTimeOfOrder(product.getPrice()); // Capture price at time of order

            actualOrderItems.add(orderItem);
            totalAmount += product.getPrice() * itemRequest.getQuantity();

            // Calculate price of selected extras and add to totalAmount
            if (itemRequest.getSelectedExtrasJson() != null) {
                // Assuming selectedExtrasJson is a JSON string of a list of objects with a 'price' field
                try {
                    // Note: org.json classes (JSONArray, JSONObject) typically come from the 'org.json:json' dependency.
                    // If you encounter a ClassNotFoundException here, add it to your pom.xml:
                    // <dependency>
                    //    <groupId>org.json</groupId>
                    //    <artifactId>json</artifactId>
                    //    <version>20240303</version> <!-- Or a recent stable version -->
                    // </dependency>
                    org.json.JSONArray jsonArray = new org.json.JSONArray(itemRequest.getSelectedExtrasJson());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        org.json.JSONObject extra = jsonArray.getJSONObject(i);
                        if (extra.has("price")) {
                            totalAmount += extra.getDouble("price") * itemRequest.getQuantity();
                        }
                    }
                } catch (org.json.JSONException e) {
                    System.err.println("Error parsing selectedExtrasJson for order item: " + e.getMessage());
                }
            }
        }
        order.setTotalAmount(totalAmount); // Set calculated total amount

        // Save the order (this will also cascade save the order items)
        Order savedOrder = orderRepository.save(order);

        // --- NEW: Wrap email sending in a separate try-catch block ---
        // This ensures that if email sending fails, the order is still committed to the database.
        try {
            // Populate order.user and order.orderItems.product if they are lazy loaded
            // To ensure all details are available for PDF generation
            User orderUser = savedOrder.getUser(); // This will trigger loading if lazy
            // Ensure products within order items are loaded before PDF generation
            savedOrder.getOrderItems().forEach(item -> {
                if (item.getProduct() != null) {
                    item.getProduct().getName(); // Access to trigger lazy loading of product name
                }
            });

            // 1. Send customer confirmation email
            byte[] customerPdf = emailService.generateCustomerOrderPdf(savedOrder, orderUser);
            String customerSubject = "KasiKotas: Your Order #" + savedOrder.getId() + " Confirmation";
            String customerBody = "Dear " + (orderUser.getFirstName() != null ? orderUser.getFirstName() : "Customer") + ",\n\n" +
                    "Thank you for your order from KasiKotas. Your order #" + savedOrder.getId() + " has been placed successfully.\n" +
                    "Please find the attached PDF for full details.\n\n" +
                    "We will notify you once your order status changes.\n\n" +
                    "Best regards,\n" +
                    "The KasiKotas Team";
            emailService.sendEmailWithAttachment(orderUser.getEmail(), customerSubject, customerBody, customerPdf, "KasiKotas_Order_" + savedOrder.getId() + ".pdf");
            System.out.println("Customer email initiated for order ID: " + savedOrder.getId());

            // 2. Send admin notification email
            byte[] adminPdf = emailService.generateAdminOrderNotificationPdf(savedOrder);
            String adminSubject = "NEW KasiKotas Order Placed: #" + savedOrder.getId();
            String adminBody = "A new order #" + savedOrder.getId() + " has been placed by " +
                    (orderUser.getFirstName() != null ? orderUser.getFirstName() + " " + orderUser.getLastName() : "A customer") +
                    " (" + orderUser.getEmail() + ").\n\n" +
                    "Please find the attached PDF for full order details.\n\n" +
                    "KasiKotas Automated Notification";
            emailService.sendEmailWithAttachment(adminEmail, adminSubject, adminBody, adminPdf, "KasiKotas_Admin_Notification_Order_" + savedOrder.getId() + ".pdf");
            System.out.println("Admin email initiated for order ID: " + savedOrder.getId());

        } catch (MessagingException e) {
            System.err.println("Failed to send order confirmation email (MessagingException): " + e.getMessage());
            // Log the error, but do not re-throw as the order itself was successfully placed.
            // In a real application, you might use a separate queue for emails or retry logic.
        } catch (Exception e) { // Catch general exceptions during PDF generation or other unexpected issues
            System.err.println("Error generating PDF or sending email (General Exception): " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for more detailed debugging
            // Log the error, but do not re-throw.
        }
        // --- END NEW ---

        return savedOrder;
    }

    /**
     * Retrieves all orders (typically for admin use).
     * @return A list of all Order objects.
     */
    public List<Order> getAllOrders() {
        // Eagerly fetch user and order items for direct use
        List<Order> orders = orderRepository.findAll();
        orders.forEach(order -> {
            order.getUser(); // Trigger lazy loading of user
            order.getOrderItems().forEach(orderItem -> orderItem.getProduct()); // Trigger lazy loading of product in each item
        });
        return orders;
    }

    /**
     * Retrieves an order by its ID.
     * @param id The ID of the order to retrieve.
     * @return An Optional containing the Order if found, or empty if not found.
     */
    public Optional<Order> getOrderById(Long id) {
        // Eagerly fetch user and order items for direct use
        Optional<Order> orderOptional = orderRepository.findById(id);
        orderOptional.ifPresent(order -> {
            order.getUser(); // Trigger lazy loading of user
            order.getOrderItems().forEach(orderItem -> orderItem.getProduct()); // Trigger lazy loading of product
        });
        return orderOptional;
    }

    /**
     * Retrieves all orders for a specific user.
     * @param userId The ID of the user whose orders to retrieve.
     * @return A list of Order objects for the given user.
     */
    public List<Order> getOrdersByUserId(Long userId) {
        // Find the user first
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return Collections.emptyList(); // Return empty list if user not found
        }
        // Eagerly fetch order items and products for display on frontend
        List<Order> orders = orderRepository.findByUser(userOptional.get());
        // Manually initialize lazy collections/entities before returning, if not using @EntityGraph
        orders.forEach(order -> {
            order.getOrderItems().forEach(orderItem -> {
                if (orderItem.getProduct() != null) { // Defensive check
                    orderItem.getProduct().getName(); // Access to trigger lazy loading of product name
                }
            });
        });
        return orders;
    }

    /**
     * Updates the status of an existing order.
     * Includes validation for status transitions (optional, but good practice).
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return An Optional containing the updated Order if found, or empty if not found.
     * @throws IllegalArgumentException if the new status is invalid for the current order state.
     */
    public Optional<Order> updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    // Example of status transition validation:
                    // You might want to prevent setting status directly from DELIVERED to PENDING
                    if (order.getStatus() == Order.OrderStatus.DELIVERED && newStatus == Order.OrderStatus.PENDING) {
                        throw new IllegalArgumentException("Cannot change status from DELIVERED to PENDING.");
                    }
                    order.setStatus(newStatus);
                    return orderRepository.save(order);
                });
    }

    /**
     * Deletes an order by its ID.
     * IMPORTANT: Deleting an order should ideally reverse stock changes if the order
     * was cancelled before shipping (e.g., if status was PENDING or PROCESSING).
     * For simplicity, this basic delete currently does NOT automatically reverse stock.
     * You would add that logic here if needed, based on the `oldStatus`.
     *
     * @param id The ID of the order to delete.
     * @return true if the order was found and deleted, false otherwise.
     */
    public boolean deleteOrder(Long id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Counts the total number of orders in the system (all time).
     * This method will be used for the persistent order limit.
     * @return The total count of orders.
     */
    public long countTotalOrders() {
        return orderRepository.count();
    }
}
