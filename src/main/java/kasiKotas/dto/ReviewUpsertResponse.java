package kasiKotas.dto;

import kasiKotas.dto.ReviewResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewUpsertResponse {
    private String action; // CREATED or UPDATED
    private ReviewResponse review;
}
