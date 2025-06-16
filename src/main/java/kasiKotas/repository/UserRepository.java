// src/main/java/kasiKotas/repository/UserRepository.java
package kasiKotas.repository;

import kasiKotas.model.User; // Import the User entity
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // For methods that might return null

/**
 * Spring Data JPA repository for the User entity.
 * Provides standard CRUD operations for User entities.
 *
 * It extends JpaRepository, which automatically handles basic
 * database interactions for the 'users' table.
 */
@Repository // Marks this interface as a Spring Data repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Custom query method: find a User by their email.
    // Spring Data JPA will automatically generate the query for this method.
    Optional<User> findByEmail(String email);

    // You can add more custom query methods here if needed, for example:
    // List<User> findByRole(User.UserRole role);
    // List<User> findByFirstNameContainingIgnoreCase(String firstName);
}
