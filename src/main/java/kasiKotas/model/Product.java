// src/main/java/kasiKotas/model/Product.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single product (Kota) in the e-commerce system.
 *
 * Images are referenced using imageUrl rather than storing the actual
 * image bytes inside the database. This keeps product API responses
 * lightweight and improves application performance.
 */
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Double price;

    /**
     * URL of the product image.
     *
     * The actual image should be hosted externally (for example,
     * on a CDN/image hosting service) rather than stored in MySQL.
     */
    private String imageUrl;

    @Column(nullable = false)
    private Integer stock;

    /**
     * Computed stock used for display.
     * Not stored in the database.
     */
    @Transient
    private Integer effectiveStock;

    /**
     * Computed availability.
     * Not stored in the database.
     */
    @Transient
    private Boolean available;

    /**
     * Computed average review rating.
     * Not stored in the database.
     */
    @Transient
    private Double averageRating;

    /**
     * Computed total number of reviews.
     * Not stored in the database.
     */
    @Transient
    private Long totalReviews;

    /**
     * Optimistic locking version.
     */
    @Version
    private Long version;
}