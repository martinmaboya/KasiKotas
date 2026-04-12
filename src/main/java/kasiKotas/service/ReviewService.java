package kasiKotas.service;

import jakarta.persistence.EntityNotFoundException;
import kasiKotas.dto.CreateReviewRequest;
import kasiKotas.dto.ReviewResponse;
import kasiKotas.dto.ReviewSummaryResponse;
import kasiKotas.dto.ReviewUpsertResponse;
import kasiKotas.model.Product;
import kasiKotas.model.Review;
import kasiKotas.model.User;
import kasiKotas.repository.OrderRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.ReviewRepository;
import kasiKotas.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    public ReviewUpsertResponse createOrUpdateReview(Long productId, CreateReviewRequest request, String userEmail) {
        validateRequest(request);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

        // Check if user has completed order containing this product
        boolean hasCompletedOrder = orderRepository.hasCompletedOrderWithProduct(user.getId(), productId);
        if (!hasCompletedOrder) {
            throw new IllegalArgumentException("You can only review products you have ordered and received.");
        }

        var existingReview = reviewRepository.findByProductIdAndUserId(productId, user.getId());
        boolean isNewReview = existingReview.isEmpty();
        Review review = existingReview.orElseGet(() -> Review.builder().product(product).user(user).build());

        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));

        ReviewResponse response = toResponse(reviewRepository.save(review));
        return ReviewUpsertResponse.builder()
                .action(isNewReview ? "CREATED" : "UPDATED")
                .review(response)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getProductReviews(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }

        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getProductReviewSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }

        List<ReviewRepository.ProductReviewSummaryProjection> summaries =
                reviewRepository.findReviewSummariesByProductIds(List.of(productId));

        double averageRating = 0.0;
        long totalReviews = 0L;
        if (!summaries.isEmpty()) {
            ReviewRepository.ProductReviewSummaryProjection summary = summaries.get(0);
            averageRating = summary.getAverageRating() == null ? 0.0 : summary.getAverageRating();
            totalReviews = summary.getTotalReviews() == null ? 0L : summary.getTotalReviews();
        }

        return ReviewSummaryResponse.builder()
                .productId(productId)
                .averageRating(Math.round(averageRating * 10.0) / 10.0)
                .totalReviews(totalReviews)
                .build();
    }

    public void deleteReview(Long productId, Long reviewId, String userEmail) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));

        if (!review.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Review does not belong to this product.");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only delete your own review.");
        }

        reviewRepository.delete(review);
    }

    private void validateRequest(CreateReviewRequest request) {
        if (request == null || request.getRating() == null) {
            throw new IllegalArgumentException("Rating is required.");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        if (request.getComment() != null && request.getComment().trim().length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment must be at most " + MAX_COMMENT_LENGTH + " characters.");
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ReviewResponse toResponse(Review review) {
        String reviewerName = review.getUser().getFirstName() + " " + review.getUser().getLastName();
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .userId(review.getUser().getId())
                .reviewerName(reviewerName.trim())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
