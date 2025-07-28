// src/main/java/kasiKotas/model/Extra.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents an extra/add-on item that can be added to an order item.
 * Examples: "Extra Cheese", "No Onions", "Add Bacon".
 * This entity will map to an 'extras' table in the database.
 */
@Entity
@Table(name = "extras") // Maps to the 'extras' table in the database
@Data // Lombok: Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder
public class Extra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // Name must be unique
    private String name; // e.g., "Extra Cheese", "Bacon Strips", "Exclude Onions"

    @Column(nullable = false)
    private Double price; // Price of the extra (can be 0.0 if it's an exclusion or free add-on)

    @Column(columnDefinition = "TEXT")
    private String description; // Optional description of the extra

    @Version // For optimistic locking if multiple admins modify extras concurrently
    private Long version;
}
