// src/main/java/kasiKotas/model/User.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.ArrayList; // For initializing collections

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import for JsonIgnoreProperties

/**
 * Represents a user (customer or admin) in the KasiKotas e-commerce system.
 * This entity will store user details and their associated orders.
 *
 * @JsonIgnoreProperties is added to break potential infinite recursion during JSON serialization.
 * When a User object is serialized, its 'orders' list will be included. If Order
 * then tries to serialize its 'user', it would create a loop. By ignoring 'user'
 * when serializing 'orders' (in the Order entity), we prevent this.
 */
@Entity
@Table(name = "users") // Maps to the 'users' table in the database
@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // Email must be unique
    private String email;

    @Column(nullable = false)
    private String password; // Store hashed passwords, NOT plain text!

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String address; // Shipping address
    private String phoneNumber;
    private String roomNumber; // Added for frontend consistency (Room Number)


    @Enumerated(EnumType.STRING) // Stores enum as String in DB
    @Column(nullable = false)
    private UserRole role; // Role of the user (e.g., CUSTOMER, ADMIN)

    // One-to-Many relationship with Order: One user can have many orders
    // 'mappedBy' indicates the field in the Order entity that owns the relationship.
    // 'cascade = CascadeType.ALL' means that if a user is deleted, all their orders are also deleted.
    // 'orphanRemoval = true' ensures that if an order is removed from the user's order list, it's also removed from the DB.
    // @JsonIgnoreProperties is crucial here: when serializing a User, we don't want to
    // follow the 'orders' list back to 'order.user' as that would cause infinite recursion.
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("user") // Ignore the 'user' property when serializing 'Order' from 'User'
    private List<Order> orders = new ArrayList<>(); // Initialize to prevent NullPointerExceptions

    /**
     * Enum for user roles.
     */
    public enum UserRole {
        CUSTOMER,
        ADMIN
    }
}
