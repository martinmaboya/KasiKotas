package kasiKotas.service;

import kasiKotas.dto.ProductExtraRequirementRequest;
import kasiKotas.model.Extra;
import kasiKotas.model.Product;
import kasiKotas.model.ProductExtraRequirement;
import kasiKotas.repository.ExtraRepository;
import kasiKotas.repository.ProductExtraRequirementRepository;
import kasiKotas.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProductExtraRequirementService {

    private final ProductExtraRequirementRepository requirementRepository;
    private final ProductRepository productRepository;
    private final ExtraRepository extraRepository;

    @Autowired
    public ProductExtraRequirementService(ProductExtraRequirementRepository requirementRepository,
                                          ProductRepository productRepository,
                                          ExtraRepository extraRepository) {
        this.requirementRepository = requirementRepository;
        this.productRepository = productRepository;
        this.extraRepository = extraRepository;
    }

    public List<ProductExtraRequirement> getRequirementsForProduct(Long productId) {
        return requirementRepository.findByProductId(productId);
    }

    public List<ProductExtraRequirement> replaceRequirements(Long productId, List<ProductExtraRequirementRequest> requests) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        requirementRepository.deleteByProductId(productId);

        List<ProductExtraRequirement> requirements = new ArrayList<>();
        for (ProductExtraRequirementRequest request : requests) {
            if (request.getExtraId() == null) {
                throw new IllegalArgumentException("extraId is required.");
            }
            if (request.getUnitsRequired() == null || request.getUnitsRequired() <= 0) {
                throw new IllegalArgumentException("unitsRequired must be greater than zero.");
            }

            Extra extra = extraRepository.findById(request.getExtraId())
                    .orElseThrow(() -> new IllegalArgumentException("Extra not found: " + request.getExtraId()));

            requirements.add(ProductExtraRequirement.builder()
                    .product(product)
                    .extra(extra)
                    .unitsRequired(request.getUnitsRequired())
                    .build());
        }

        return requirementRepository.saveAll(requirements);
    }
}

