// src/main/java/kasiKotas/repository/SauceRepository.java
package kasiKotas.repository;

import kasiKotas.model.Sauce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Sauce entity.
 * Provides standard CRUD (Create, Read, Update, Delete) operations
 * for managing sauce items in the 'sauces' table.
 */
@Repository
public interface SauceRepository extends JpaRepository<Sauce, Long> {
    // JpaRepository provides methods like save(), findById(), findAll(), deleteById(), etc.
    // No custom query methods are immediately needed here.
}
