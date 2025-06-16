// src/main/java/kasiKotas/repository/BankDetailsRepository.java
package kasiKotas.repository;

import kasiKotas.model.BankDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the BankDetails entity.
 * Provides standard CRUD operations for BankDetails entities.
 *
 * This repository will manage interactions with the 'bank_details' table.
 */
@Repository // Marks this interface as a Spring Data repository
public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {
    // JpaRepository provides methods like save(), findById(), findAll(), deleteById(), etc.
    // Since we anticipate only one set of bank details, findById(1L) or findAll() and taking the first
    // will be common operations.
}
