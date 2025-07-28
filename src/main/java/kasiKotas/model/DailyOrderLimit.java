// src/main/java/kasiKotas/model/DailyOrderLimit.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents the single configurable total order limit for the system.
 * This entity will store a single record in the database, defining
 * the maximum number of orders allowed overall.
 * We'll typically manage this record with a fixed ID (e.g., 1L).
 */
@Entity
@Table(name = "daily_order_limit") // Renaming table is optional if it's already created, but reflects new purpose
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder
public class DailyOrderLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int limitValue; // The maximum total number of orders allowed
}
