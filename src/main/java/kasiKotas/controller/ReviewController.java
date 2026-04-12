package kasiKotas.controller;

import jakarta.persistence.EntityNotFoundException;
import kasiKotas.dto.CreateReviewRequest;
import kasiKotas.dto.ReviewResponse;
import kasiKotas.dto.ReviewSummaryResponse;
import kasiKotas.dto.ReviewUpsertResponse;
import kasiKotas.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @Autowired
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getProductReviews(@PathVariable Long productId) {
        try {
            return ResponseEntity.ok(reviewService.getProductReviews(productId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummaryResponse> getProductReviewSummary(@PathVariable Long productId) {
        try {
            return ResponseEntity.ok(reviewService.getProductReviewSummary(productId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<Object> createOrUpdateReview(@PathVariable Long productId,
                                                        @RequestBody CreateReviewRequest request,
                                                        Authentication authentication) {
        try {
            ReviewUpsertResponse saved = reviewService.createOrUpdateReview(productId, request, authentication.getName());
            HttpStatus status = "CREATED".equals(saved.getAction()) ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Object> deleteReview(@PathVariable Long productId,
                                               @PathVariable Long reviewId,
                                               Authentication authentication) {
        try {
            reviewService.deleteReview(productId, reviewId, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
