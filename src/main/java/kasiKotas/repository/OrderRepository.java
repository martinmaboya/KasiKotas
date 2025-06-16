// src/main/java/kasiKotas/repository/OrderRepository.java
package kasiKotas.repository;

import kasiKotas.model.Order; // Import the Order entity
import kasiKotas.model.User;  // Import the User entity (for finding orders by user)
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime; // This import is still here but the specific method requiring it is removed
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the Order entity.
 * Provides standard CRUD operations for Order entities.
 *
 * It extends JpaRepository, which automatically handles basic
 * database interactions for the 'orders' table.
 */
@Repository // Marks this interface as a Spring Data repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Custom query method: find all Orders by a specific User.
    // This leverages the Many-to-One relationship defined in the Order entity.
    List<Order> findByUser(User user);

    // Custom query method: find orders by status
    // List<Order> findByStatus(Order.OrderStatus status);

    // Custom query method: find orders by a user and a specific status
    // List<Order> findByUserAndStatus(User user, Order.OrderStatus status);

    // Removed: long countByOrderDateBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);
    // This method is removed as the requirement is now for a total order limit, not a daily one.
    // The default JpaRepository.count() method will be used to get the total number of orders.
}
