// src/main/java/kasiKotas/repository/ExtraRepository.java
package kasiKotas.repository;

import kasiKotas.model.Extra;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the Extra entity.
 * Provides standard CRUD (Create, Read, Update, Delete) operations
 * for managing add-on items in the 'extras' table.
 */
@Repository
public interface ExtraRepository extends JpaRepository<Extra, Long> {
    // JpaRepository provides methods like save(), findById(), findAll(), deleteById(), etc.

    @Modifying
    @Query("UPDATE Extra e SET e.stock = e.stock - :quantity WHERE e.id = :extraId AND e.stock >= :quantity")
    int decrementStockIfAvailable(@Param("extraId") Long extraId, @Param("quantity") int quantity);

    Optional<Extra> findByNameIgnoreCase(String name);
}
