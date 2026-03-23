package kasiKotas.dto;

import lombok.Data;

@Data
public class ProductExtraRequirementRequest {
    private Long extraId;
    private Integer unitsRequired;
}

