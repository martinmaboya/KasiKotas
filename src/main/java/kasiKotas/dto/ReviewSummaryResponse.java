package kasiKotas.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewSummaryResponse {
    private Long productId;
    private double averageRating;
    private long totalReviews;
}

