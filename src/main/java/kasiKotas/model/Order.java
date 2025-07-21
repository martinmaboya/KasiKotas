// src/main/java/kasiKotas/model/Order.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference; // Added import for TypeReference
import com.fasterxml.jackson.databind.ObjectMapper; // Added import for ObjectMapper

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a customer's order in the KasiKotas system.
 * This is a JPA Entity, mapping to an 'orders' table in the database.
 *
 * Includes relationships to User and OrderItems, and now a paymentMethod field.
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

    // Many-to-one relationship with User
    // FetchType.LAZY means the User data is loaded only when it's accessed.
    // @JoinColumn specifies the foreign key column in the 'orders' table.
    // JsonIgnoreProperties is crucial here to prevent infinite recursion/lazy loading issues
    // when serializing Order (which has a User) and User (which might have Orders).
    @ManyToOne(fetch = FetchType.LAZY) // Keeping LAZY as it's generally better for performance
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders"}) // Ignore 'orders' in User to break loop
    private User user;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING) // Stores the enum name (e.g., "PENDING") as a string in the DB
    @Column(nullable = false)
    private OrderStatus status; // PENDING, PROCESSING, DELIVERED, CANCELLED, etc.

    @Column(nullable = false)
    private Double totalAmount;

    @Column(columnDefinition = "TEXT") // Use TEXT for potentially longer addresses
    private String shippingAddress;

    // NEW: Field for payment method (e.g., "COD", "EFT")
    @Column(nullable = false)
    private String paymentMethod; // e.g., "cod", "eft"

    // NEW: Field for scheduled delivery time (null for immediate delivery)
    @Column(name = "scheduled_delivery_time")
    private LocalDateTime scheduledDeliveryTime;

    // One-to-Many relationship with OrderItem
    // 'mappedBy' indicates that the 'order' field in the OrderItem entity owns the relationship.
    // CascadeType.ALL means that operations (like persist, remove) on the Order will cascade to its OrderItems.
    // orphanRemoval = true means if an OrderItem is removed from the 'orderItems' list, it's also deleted from DB.
    // FetchType.LAZY is used for performance, loading items only when accessed.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("order") // Ignore the 'order' field within the 'orderItems' list when serializing Order
    private List<OrderItem> orderItems;

    @Version // For optimistic locking to handle concurrent updates gracefully
    private Long version;

    // Removed promo code fields based on previous instructions
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "promo_code_id")
    // @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders"})
    // private PromoCode appliedPromoCode;
    //
    // private Double discountAmount;


    /**
     * Enum for defining possible order statuses.
     */
    public enum OrderStatus {
        PENDING,
        PROCESSING,
        DELIVERED,
        CANCELLED,
        COLLECTED, // For collection orders
        READY,    // For delivery orders
    }

    /**
     * Calculates the total amount of the order by summing up prices of all order items.
     * This method is typically called before saving the order to ensure total amount is accurate.
     * Promo code logic has been removed.
     */
    @PrePersist // Called before the entity is first persisted (inserted)
    @PreUpdate  // Called before the entity is updated
    public void calculateTotalAmount() {
        if (orderItems == null) {
            this.totalAmount = 0.0;
            return;
        }

        double calculatedTotal = 0.0;
        for (OrderItem item : orderItems) {
            // Ensure priceAtTimeOfOrder is set before calculation
            if (item.getPriceAtTimeOfOrder() != null && item.getQuantity() != null) {
                calculatedTotal += item.getPriceAtTimeOfOrder() * item.getQuantity();
            }

            // Include extra prices in total
            if (item.getSelectedExtrasJson() != null && !item.getSelectedExtrasJson().isEmpty()) {
                try {
                    // Using a new ObjectMapper here for simplicity within an entity method.
                    // In a service, you would inject it.
                    ObjectMapper mapper = new ObjectMapper();
                    List<Extra> selectedExtras = mapper.readValue(item.getSelectedExtrasJson(), new TypeReference<List<Extra>>() {});
                    for (Extra extra : selectedExtras) {
                        calculatedTotal += (extra.getPrice() * item.getQuantity());
                    }
                } catch (Exception e) {
                    System.err.println("Error calculating total with extras for order item: " + e.getMessage());
                    // Log the error, but don't prevent total calculation
                }
            }
            // Sauces are free, so no price addition for them here.
            // We can explicitly check for selectedSaucesJson, though it won't impact total.
            if (item.getSelectedSaucesJson() != null && !item.getSelectedSaucesJson().isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    // Just parsing to ensure it's valid JSON if needed for debugging, no price impact
                    mapper.readValue(item.getSelectedSaucesJson(), new TypeReference<List<Sauce>>() {});
                } catch (Exception e) {
                    System.err.println("Error parsing selectedSaucesJson during total amount calculation for order item: " + e.getMessage());
                }
            }
        }

        // Removed discount application logic
        // if (this.appliedPromoCode != null && this.discountAmount != null) {
        //     calculatedTotal = Math.max(0, calculatedTotal - this.discountAmount);
        // }

        this.totalAmount = calculatedTotal;
    }
}
