// src/main/java/kasiKotas/repository/ProductRepository.java
package kasiKotas.repository; // This specifies the package for this interface

import kasiKotas.model.Product; // Import the Product entity
import org.springframework.data.jpa.repository.JpaRepository; // Core Spring Data JPA interface
import org.springframework.stereotype.Repository; // Annotation to mark this as a Spring component

import java.util.List; // For potential custom query methods

/**
 * Spring Data JPA repository for the Product entity.
 * This interface extends JpaRepository, which automatically provides
 * standard CRUD (Create, Read, Update, Delete) operations for the Product entity.
 *
 * Spring Data JPA will automatically implement this interface at runtime.
 * You don't need to write any implementation code for it.
 *
 * The two type arguments for JpaRepository are:
 * 1. The Entity type it manages (Product in this case).
 * 2. The type of the Entity's primary key (Long for Product's 'id').
 */
@Repository // Marks this interface as a Spring Data repository, making it discoverable by Spring's component scanning.
public interface ProductRepository extends JpaRepository<Product, Long> {

    // JpaRepository provides methods like:
    // - save(Product product): Saves a product (inserts or updates).
    // - findById(Long id): Retrieves a product by its ID.
    // - findAll(): Retrieves all products.
    // - deleteById(Long id): Deletes a product by its ID.
    // - count(): Returns the number of products.
    // ... and many more.

    // You can also define custom query methods here by simply declaring their signatures.
    // Spring Data JPA will derive the query automatically based on the method name.
    // Examples (you can add these if needed later):

    // Find products by name containing a specific string (case-insensitive)
    // List<Product> findByNameContainingIgnoreCase(String name);

    // Find products by price less than a given amount
    // List<Product> findByPriceLessThan(Double price);

    // Find products where stock is less than a certain value
    // List<Product> findByStockLessThan(Integer stock);
}