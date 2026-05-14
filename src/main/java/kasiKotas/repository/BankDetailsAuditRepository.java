package kasiKotas.repository;

import kasiKotas.model.BankDetailsAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankDetailsAuditRepository extends JpaRepository<BankDetailsAudit, Long> {
    List<BankDetailsAudit> findAllByOrderByChangedAtDesc();
}

