// src/main/java/kasiKotas/repository/DailyOrderLimitRepository.java
package kasiKotas.repository;

import kasiKotas.model.DailyOrderLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DailyOrderLimit entity.
 * Provides standard CRUD operations for managing the daily order limit.
 * Since we anticipate a single entry, methods like findAll() or findById(1L)
 * will be used to retrieve it.
 */
@Repository // Marks this interface as a Spring Data repository
public interface DailyOrderLimitRepository extends JpaRepository<DailyOrderLimit, Long> {
    // JpaRepository provides all necessary methods like save(), findById(), findAll(), etc.
    // No custom methods are strictly needed here for a single-record scenario.
}
