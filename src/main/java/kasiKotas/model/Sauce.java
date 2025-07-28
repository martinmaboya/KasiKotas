// src/main/java/kasiKotas/model/Sauce.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents a sauce that can be selected for an order item.
 * Sauces are currently free of charge.
 * This entity will map to a 'sauces' table in the database.
 */
@Entity
@Table(name = "sauces") // Maps to the 'sauces' table in the database
@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder // Lombok: Enables builder pattern
public class Sauce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // Name must be unique
    private String name; // e.g., "Tomato Sauce", "Peri-Peri", "Sweet Chili"

    // Although sauces are free, keeping a price field for consistency and future flexibility
    @Column(nullable = false)
    private Double price = 0.0; // Price of the sauce (default to 0.0 for free sauces)

    @Column(columnDefinition = "TEXT")
    private String description; // Optional description of the sauce

    @Version // For optimistic locking if multiple admins modify sauces concurrently
    private Long version;
}
