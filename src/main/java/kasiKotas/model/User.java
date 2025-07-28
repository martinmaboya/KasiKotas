// src/main/java/kasiKotas/model/User.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data; // For boilerplate code (getters, setters, etc.)
import lombok.NoArgsConstructor; // For no-argument constructor
import lombok.AllArgsConstructor; // For all-argument constructor
import lombok.Builder; // For builder pattern
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // For JSON serialization issues
import java.util.List; // For the one-to-many relationship with Order

/**
 * Represents a user in the KasiKotas system, who can be a customer or an admin.
 * This is a JPA Entity, mapping to a 'users' table in the database.
 *
 * Includes optimistic locking for concurrent updates and a custom constructor
 * for creating a user object with only an ID, useful for associations.
 */
@Entity // Marks this class as a JPA entity
@Table(name = "users") // Specifies the table name in the database
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode methods
@NoArgsConstructor // Lombok: Generates a no-argument constructor (required by JPA)
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder // Lombok: Enables builder pattern
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders"}) // Ignore Hibernate's proxy fields and 'orders' to prevent infinite recursion
public class User {

    @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Configures auto-incrementing ID
    private Long id;

    @Column(nullable = false, unique = true) // Email must be unique and not null
    private String email;

    @Column(nullable = false)
    private String password; // IMPORTANT: In real apps, store hashed passwords!

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String address; // Optional: Can be used for a general residential address
    private String roomNumber; // Specific to student accommodation, etc.
    private String phoneNumber;

    @Enumerated(EnumType.STRING) // Stores the enum name (e.g., "CUSTOMER", "ADMIN") as a string in the DB
    @Column(nullable = false)
    private UserRole role; // Role of the user (e.g., CUSTOMER, ADMIN)

    @Version // For optimistic locking to handle concurrent updates gracefully
    private Long version;

    // One-to-Many relationship with orders.
    // 'mappedBy' indicates that the 'user' field in the Order entity owns the relationship.
    // FetchType.LAZY is used to prevent fetching all orders whenever a user is loaded, improving performance.
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // @JsonIgnoreProperties is necessary here to prevent infinite recursion when serializing User -> Order -> User
    @JsonIgnoreProperties("user") // Ignore the 'user' field within the 'orders' list when serializing User
    private List<Order> orders;

    /**
     * Custom constructor to create a User object with only an ID.
     * This is useful for associating an Order with a User when only the User's ID is known,
     * without fetching the full User entity from the database.
     * JPA will often use a proxy if only the ID is provided, and fill in details later.
     * @param id The ID of the user.
     */
    public User(Long id) {
        this.id = id;
    }

    /**
     * Enum for defining user roles.
     * Can be easily extended for more roles (e.g., DELIVERY_PERSON).
     */
    public enum UserRole {
        CUSTOMER,
        ADMIN
    }
}
