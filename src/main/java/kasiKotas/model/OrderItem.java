// src/main/java/kasiKotas/model/OrderItem.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonBackReference; // Import for JsonBackReference
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // General purpose for Hibernate proxies

/**
 * Represents an individual item within an Order.
 * This is a JPA Entity, mapping to an 'order_items' table in the database.
 * It links to a Product and an Order, and captures the state of the product
 * (e.g., price) at the time the order was placed.
 *
 * @JsonBackReference is used to handle bidirectional relationships and prevent infinite recursion.
 */
@Entity
@Table(name = "order_items") // Specifies the table name in the database
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Ignore Hibernate's internal proxy fields
public class OrderItem {

    @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Configures auto-incrementing ID
    private Long id;

    // Many-to-One relationship with Order: multiple order items belong to one order
    @ManyToOne(fetch = FetchType.LAZY) // Lazy loading for performance
    @JoinColumn(name = "order_id", nullable = false) // Foreign key column
    @JsonBackReference // RE-ADDED: This tells Jackson to ignore this side when serializing OrderItem.
    private Order order;

    // Many-to-One relationship with Product: multiple order items can reference one product
    // Note: Product is typically a simple object not needing @JsonBackReference unless Product itself has a collection of OrderItems.
    // If Product had a List<OrderItem>, then OrderItem.product would be @JsonBackReference.
    // For now, assume Product is the root of its own graph.
    @ManyToOne(fetch = FetchType.LAZY) // Lazy loading for performance
    @JoinColumn(name = "product_id", nullable = false) // Foreign key column
    private Product product; // Product is eager-loaded by OrderService when converting to DTO

    @Column(nullable = false)
    private Integer quantity; // Quantity of this product in the order

    @Column(nullable = false)
    private Double priceAtTimeOfOrder; // Price of the product when the order was placed (important for historical accuracy)

    @Column(columnDefinition = "TEXT") // Use TEXT for potentially longer strings
    private String customizationNotes; // Notes from the user for this specific item (e.g., "no pickles")

    // NEW FIELD: To store selected extras as a JSON string
    // This allows flexible storage of a list of extra items with their names and prices
    @Column(columnDefinition = "TEXT")
    private String selectedExtrasJson; // JSON string representing selected extras

    @Column(columnDefinition = "TEXT") // NEW: JSON string representing selected sauces
    private String selectedSaucesJson;
}
