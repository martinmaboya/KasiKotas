// src/main/java/kasiKotas/repository/ExtraRepository.java
package kasiKotas.repository;

import kasiKotas.model.Extra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Extra entity.
 * Provides standard CRUD (Create, Read, Update, Delete) operations
 * for managing add-on items in the 'extras' table.
 */
@Repository
public interface ExtraRepository extends JpaRepository<Extra, Long> {
    // JpaRepository provides methods like save(), findById(), findAll(), deleteById(), etc.
    // No custom query methods are immediately needed here.
}
