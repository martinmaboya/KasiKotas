package kasiKotas.repository;

import kasiKotas.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface ProductReviewSummaryProjection {
        Long getProductId();
        Double getAverageRating();
        Long getTotalReviews();
    }

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    @Query("SELECT r.product.id AS productId, AVG(r.rating) AS averageRating, COUNT(r.id) AS totalReviews " +
            "FROM Review r WHERE r.product.id IN :productIds GROUP BY r.product.id")
    List<ProductReviewSummaryProjection> findReviewSummariesByProductIds(@Param("productIds") List<Long> productIds);
}
