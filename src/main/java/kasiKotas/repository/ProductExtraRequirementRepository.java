package kasiKotas.repository;

import kasiKotas.model.ProductExtraRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductExtraRequirementRepository extends JpaRepository<ProductExtraRequirement, Long> {

    List<ProductExtraRequirement> findByProductId(Long productId);

    void deleteByProductId(Long productId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM ProductExtraRequirement r WHERE r.product.id = :productId AND r.extra.stock < r.unitsRequired")
    boolean hasInsufficientRequiredExtra(@Param("productId") Long productId);
}

