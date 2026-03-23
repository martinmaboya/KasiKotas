package kasiKotas.controller;

import kasiKotas.dto.ProductExtraRequirementRequest;
import kasiKotas.model.ProductExtraRequirement;
import kasiKotas.service.ProductExtraRequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/required-extras")
public class ProductExtraRequirementController {

    private final ProductExtraRequirementService requirementService;

    @Autowired
    public ProductExtraRequirementController(ProductExtraRequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @GetMapping
    public ResponseEntity<List<ProductExtraRequirement>> getRequirements(@PathVariable Long productId) {
        return ResponseEntity.ok(requirementService.getRequirementsForProduct(productId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<?> replaceRequirements(@PathVariable Long productId,
                                                 @RequestBody List<ProductExtraRequirementRequest> requests) {
        try {
            return ResponseEntity.ok(requirementService.replaceRequirements(productId, requests));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}

