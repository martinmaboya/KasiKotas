// src/main/java/kasiKotas/model/Order.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a customer's order in the KasiKotas system.
 * This entity tracks the order's details, including the customer,
 * total amount, status, and associated order items.
 *
 * @JsonIgnoreProperties is added to prevent serialization issues with
 * lazy-loaded collections and Hibernate internal properties.
 */
@Entity
@Table(name = "orders") // Maps to the 'orders' table in the database
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orderItems"}) // Ignore Hibernate's internal fields and avoid circular reference with orderItems
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-One relationship with User: Many orders can belong to one user
    @ManyToOne(fetch = FetchType.LAZY) // Lazy fetching for performance
    @JoinColumn(name = "user_id", nullable = false) // Foreign key to the 'users' table
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders"}) // Ignore Hibernate's internal fields and avoid circular reference with orders
    private User user;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING) // Store enum as String in DB
    @Column(nullable = false)
    private OrderStatus status; // PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED

    @Column(nullable = false)
    private String shippingAddress;

    // One-to-Many relationship with OrderItem: One order can have many order items
    // CascadeType.ALL means all operations (persist, merge, remove) on Order will cascade to OrderItem
    // orphanRemoval = true means if an OrderItem is removed from the orderItems list, it will be deleted from the DB
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "order"}) // Ignore Hibernate's internal fields and avoid circular reference with order
    private List<OrderItem> orderItems;

    /**
     * Enum for possible order statuses.
     */
    public enum OrderStatus {
        PENDING,       // Order placed, awaiting confirmation/payment
        PROCESSING,    // Order confirmed, being prepared
        SHIPPED,       // Order dispatched for delivery
        DELIVERED,     // Order successfully delivered
        CANCELLED,     // Order cancelled by customer or admin
        COLLECTED
    }
}
