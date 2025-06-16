// src/main/java/kasiKotas/repository/OrderItemRepository.java
package kasiKotas.repository;

import kasiKotas.model.Order;     // Import the Order entity
import kasiKotas.model.OrderItem; // Import the OrderItem entity
import kasiKotas.model.Product;   // Import the Product entity
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the OrderItem entity.
 * Provides standard CRUD operations for OrderItem entities.
 *
 * It extends JpaRepository, which automatically handles basic
 * database interactions for the 'order_items' table.
 */
@Repository // Marks this interface as a Spring Data repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Custom query method: find all OrderItems belonging to a specific Order.
    List<OrderItem> findByOrder(Order order);

    // Custom query method: find order items by product
    // List<OrderItem> findByProduct(Product product);
}
