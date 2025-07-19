// src/main/java/kasiKotas/model/PromoCode.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * Represents a discount promo code that users can apply during checkout.
 * Example: "BURGER10" for 10% off, or R20 off orders over R100.
 */
@Entity
@Table(name = "promo_codes") // Maps to the 'promo_codes' table
@Data // Lombok: Getters, Setters, toString, equals, hashCode
@NoArgsConstructor // Lombok: No-arg constructor
@AllArgsConstructor // Lombok: All-args constructor
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., "BURGER10"

    @Column(nullable = false)
    private Double discountAmount; // e.g., 10.00 (can be percentage or fixed)

    @Column(nullable = false)
    private Boolean percentageDiscount; // true = percentage, false = fixed amount

    @Column(nullable = false)
    private Integer maxUsages; // Max times the code can be used (e.g., 10)

    @Column(nullable = false)
    private Integer usageCount = 0; // How many times it's been used

    @Column(nullable = false)
    private LocalDate expiryDate; // Date the promo code expires

    @Column(columnDefinition = "TEXT")
    private String description; // Optional: e.g., "10% off your next order"

    @Version // Optional: optimistic locking for safe concurrent updates
    private Long version;
}
