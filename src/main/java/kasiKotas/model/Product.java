// src/main/java/kasiKotas/model/Product.java
package kasiKotas.model; // This specifies the package for this class

import jakarta.persistence.*; // JPA annotations for database mapping
import lombok.Data; // Lombok for boilerplate code reduction
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder; // Import for Builder annotation
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Import for JsonIgnoreProperties

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
@Entity // Marks this class as a JPA entity. Hibernate will recognize it and map it to a database table.
@Table(name = "products") // Specifies the actual table name in the database. It will be "products".
@Data // Lombok: Generates boilerplate code (getters, setters, toString, equals, hashCode)
@NoArgsConstructor // Lombok: Generates a public no-argument constructor, required by JPA.
@AllArgsConstructor // Lombok: Generates a constructor with all fields, useful for creating instances with data.
@Builder // Lombok: Enables builder pattern
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // NEW: Ignore Hibernate's internal proxy fields during JSON serialization
public class Product {

    @Id // Marks this field as the primary key of the entity.
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Configures the primary key to be auto-incremented by the database.
    // IDENTITY is suitable for MySQL.
    private Long id; // Unique identifier for each product.

    @Column(nullable = false) // Specifies that this database column cannot contain NULL values.
    private String name; // The name of the Kota (e.g., "Classic Kota", "Vegetarian Kota"). This field is mandatory.

    @Column(nullable = false)
    private String description; // A detailed description of the Kota's ingredients and flavor. This field is mandatory.

    @Column(nullable = false)
    private Double price; // The price of the Kota. This field is mandatory.

    private String imageUrl; // An optional URL to an image of the Kota. Can be null.

    @Column(nullable = false)
    private Integer stock; // The quantity of this Kota currently available in stock. This field is mandatory.

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] image; // NEW: Image data for the product, stored as a blob in the database.

    private String imageType; // MIME type of the image (e.g., image/jpeg)

    // Note: Lombok automatically generates the getters and setters, so you don't
    // need to write them manually in this file. For example, getName(), setName(String name), etc.
}
