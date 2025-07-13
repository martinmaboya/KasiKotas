// src/main/java/kasiKotas/model/Product.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single product (Kota) in the e-commerce system.
 * This is a JPA Entity, meaning it maps directly to a table in your MySQL database.
 *
 * It uses Lombok annotations for convenience, automatically generating:
 * - Getters and setters for all fields (@Data)
 * - A no-argument constructor (@NoArgsConstructor)
 * - A constructor with all arguments (@AllArgsConstructor)
 *
 * @JsonIgnoreProperties is added to prevent serialization issues when Hibernate
 * lazy-loads proxy objects (e.g., if a Product is fetched as a proxy within an OrderItem
 * and then directly serialized without being fully initialized).
 */
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    // --- OLD: private String imageUrl; // This field would be removed or repurposed

    @Lob
    @Column(name = "image_data")
    private byte[] imageData;


    // Optional: Store the content type (e.g., "image/jpeg", "image/png")
    // This is crucial when serving the image back to the client via HTTP
    private String imageContentType;

    // Optional: Store the original filename (useful for display or debugging)
    private String imageName;
    // --- END NEW FIELDS ---

    @Column(nullable = false)
    private Integer stock;

    // Note: Lombok automatically generates the getters and setters, so you don't
    // need to write them manually in this file.
}