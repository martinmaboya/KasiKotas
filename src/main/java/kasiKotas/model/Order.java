// src/main/java/kasiKotas/model/Order.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime; // For order date/time
import java.util.List; // For order items

/**
 * Represents an Order made by a User in the e-commerce system.
 * This is a JPA Entity, mapping to an 'orders' table in the database.
 *
 * Updated to include promo code application.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-One relationship with User: multiple orders can belong to one user
    @ManyToOne(fetch = FetchType.LAZY) // Lazy fetching for performance
    @JoinColumn(name = "user_id", nullable = false) // Foreign key column
    private User user;

    @Column(nullable = false)
    private LocalDateTime orderDate; // Timestamp of when the order was placed

    @Column(nullable = false)
    private Double totalAmount; // Total amount of the order (calculated)

    @Enumerated(EnumType.STRING) // Store enum as String in DB (e.g., "PENDING", "DELIVERED")
    @Column(nullable = false)
    private OrderStatus status; // Current status of the order (PENDING, PROCESSING, DELIVERED, CANCELLED, COLLECTED)

    @Column(nullable = false)
    private String shippingAddress; // Delivery address or "Collection at store"

    // NEW: Field to store the applied promo code string
    private String appliedPromoCode; // Stores the code that was successfully applied

    // One-to-Many relationship with OrderItem: one order can have multiple items
    // CascadeType.ALL: Operations on Order (e.g., persist, remove) cascade to OrderItems
    // orphanRemoval = true: If an OrderItem is removed from the orderItems list, it's deleted from DB
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems;

    /**
     * Constructor used by the service layer to create new orders.
     * The totalAmount and orderDate are typically set within the service layer.
     */
    public Order(User user, String shippingAddress, List<OrderItem> orderItems) {
        this.user = user;
        this.shippingAddress = shippingAddress;
        this.orderItems = orderItems;
        this.orderDate = LocalDateTime.now(); // Set creation date
        this.status = OrderStatus.PENDING; // Default status for new orders
        calculateTotalAmount(); // Calculate initial total
        this.appliedPromoCode = null; // Initialize promo code to null
    }

    /**
     * Calculates the total amount of the order based on its items.
     * This method should be called whenever orderItems are added or modified.
     * It does not include promo code logic, which will be handled in the service layer.
     */
    public void calculateTotalAmount() {
        if (this.orderItems != null) {
            this.totalAmount = this.orderItems.stream()
                    .mapToDouble(item -> {
                        double itemTotal = item.getPriceAtTimeOfOrder() * item.getQuantity();
                        // Add extras price to item total
                        if (item.getSelectedExtrasJson() != null) {
                            try {
                                org.json.JSONArray jsonArray = new org.json.JSONArray(item.getSelectedExtrasJson());
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    org.json.JSONObject extra = jsonArray.getJSONObject(i);
                                    if (extra.has("price")) {
                                        itemTotal += extra.getDouble("price"); // Price per extra, not multiplied by item quantity here. Quantity already handled above.
                                    }
                                }
                            } catch (org.json.JSONException e) {
                                System.err.println("Error parsing selectedExtrasJson for order item during total calculation: " + e.getMessage());
                            }
                        }
                        return itemTotal;
                    })
                    .sum();
        } else {
            this.totalAmount = 0.0;
        }
    }

    /**
     * Enum to define possible statuses for an order.
     */
    public enum OrderStatus {
        PENDING,     // Order has been placed, awaiting processing
        PROCESSING,  // Order is being prepared/fulfilled
        DELIVERED,   // Order has been delivered to the customer
        CANCELLED,   // Order was cancelled
        COLLECTED    // Order has been collected by the customer
    }
}
