package kasiKotas.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
@Builder // Lombok: Enables builder pattern
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., "BURGER10"

    @Column(nullable = false)
    private Double discountAmount; // e.g., 10.00 (can be percentage or fixed)

    @Column(nullable = false)
    private Integer percentageDiscount; // 1 = percentage, 0 = fixed amount (using Integer for better DB compatibility)

    // Custom setter to handle both boolean and integer values from JSON
    @JsonSetter("percentageDiscount")
    public void setPercentageDiscount(Object value) {
        if (value instanceof Boolean) {
            this.percentageDiscount = (Boolean) value ? 1 : 0;
        } else if (value instanceof Integer) {
            this.percentageDiscount = (Integer) value;
        } else if (value instanceof Number) {
            this.percentageDiscount = ((Number) value).intValue();
        } else {
            this.percentageDiscount = 0; // default to fixed amount
        }
    }

    // Standard getter
    public Integer getPercentageDiscount() {
        return this.percentageDiscount;
    }

    // Convenience method to check if it's a percentage discount
    public boolean isPercentageDiscount() {
        return this.percentageDiscount != null && this.percentageDiscount == 1;
    }

    @Column(nullable = false)
    private Integer maxUsages; // Max times the code can be used (e.g., 10)

    @Column(nullable = false)
    private Integer usageCount = 0; // How many times it's been used

    @Column(nullable = false)
    private LocalDate expiryDate; // Date the promo code expires

        @Column(nullable = false)
        @Builder.Default
        private Double minimumOrderAmount = 0.0; // Minimum order amount required to apply promo

    @Column(columnDefinition = "TEXT")
    private String description; // Optional: e.g., "10% off your next order"

    @Version // Optional: optimistic locking for safe concurrent updates
    private Long version;
}
