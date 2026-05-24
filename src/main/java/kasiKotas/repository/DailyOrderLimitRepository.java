// src/main/java/kasiKotas/repository/DailyOrderLimitRepository.java
package kasiKotas.repository;

import kasiKotas.model.DailyOrderLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the DailyOrderLimit entity.
 * Provides standard CRUD operations for managing the daily order limit.
 * Since we anticipate a single entry, methods like findAll() or findById(1L)
 * will be used to retrieve it.
 */
@Repository // Marks this interface as a Spring Data repository
public interface DailyOrderLimitRepository extends JpaRepository<DailyOrderLimit, Long> {
    Optional<DailyOrderLimit> findFirstByOrderByIdAsc();

    // Locks the single configured limit row during order placement.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DailyOrderLimit> findFirstByOrderByIdAscForUpdate();
}
