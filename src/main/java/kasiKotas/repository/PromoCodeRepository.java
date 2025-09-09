package kasiKotas.repository;

import kasiKotas.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCode(String code);
    
    @Modifying
    @Transactional
    @Query("UPDATE PromoCode p SET p.usageCount = p.usageCount + 1 WHERE p.code = :code AND p.usageCount < p.maxUsages")
    int incrementUsageCount(@Param("code") String code);
    
    @Modifying
    @Transactional
    @Query("UPDATE PromoCode p SET p.usageCount = 0 WHERE p.code = :code")
    int resetUsageCount(@Param("code") String code);
}

