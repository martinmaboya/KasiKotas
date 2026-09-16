// src/main/java/kasiKotas/service/ProductService.java
package kasiKotas.service;

import kasiKotas.model.Product;
import kasiKotas.repository.ProductExtraRequirementRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.ReviewRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for managing Product related business logic.
 *
 * Product images are referenced through imageUrl instead of being stored
 * as binary data inside the database.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductExtraRequirementRepository productExtraRequirementRepository;
    private final ReviewRepository reviewRepository;

    @Autowired
    public ProductService(
            ProductRepository productRepository,
            ProductExtraRequirementRepository productExtraRequirementRepository,
            ReviewRepository reviewRepository) {

        this.productRepository = productRepository;
        this.productExtraRequirementRepository = productExtraRequirementRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * Retrieves all products from the database.
     *
     * Images are NOT loaded from the database because products now
     * contain only an imageUrl.
     */
    public List<Product> getAllProducts() {

        List<Product> products = productRepository.findAll();

        Map<Long, ReviewRepository.ProductReviewSummaryProjection> reviewSummaryByProductId =
                getReviewSummaryByProductId(products);

        return products.stream()
                .map(product ->
                        toResponseProduct(
                                product,
                                reviewSummaryByProductId.get(product.getId())
                        )
                )
                .toList();
    }

    /**
     * Retrieves a single product by ID.
     */
    public Optional<Product> getProductById(Long id) {

        return productRepository.findById(id)
                .map(product -> {

                    List<ReviewRepository.ProductReviewSummaryProjection> summaries =
                            reviewRepository.findReviewSummariesByProductIds(
                                    List.of(product.getId())
                            );

                    ReviewRepository.ProductReviewSummaryProjection summary =
                            summaries.isEmpty() ? null : summaries.get(0);

                    return toResponseProduct(product, summary);
                });
    }

    /**
     * Creates a new product.
     *
     * The uploaded image is NOT stored as a database BLOB.
     *
     * IMPORTANT:
     * For now, if an imageFile is provided, its URL must be handled by
     * the controller/upload layer or another image-storage service.
     */
    public Product createProduct(Product product, MultipartFile imageFile) {

        // Validate product name
        if (!StringUtils.hasText(product.getName())) {
            throw new IllegalArgumentException("Product name cannot be empty.");
        }

        // Validate description
        if (!StringUtils.hasText(product.getDescription())) {
            throw new IllegalArgumentException("Product description cannot be empty.");
        }

        // Validate price
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new IllegalArgumentException("Product price must be positive.");
        }

        // Validate stock
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative.");
        }

        /*
         * We intentionally do NOT do:
         *
         * product.setImage(imageFile.getBytes());
         *
         * Images are no longer stored in the database.
         *
         * imageFile is currently accepted so the controller/API does not
         * need to be changed immediately. Image hosting can be connected
         * next.
         */
        if (imageFile != null && !imageFile.isEmpty()) {

            if (imageFile.getSize() > 20 * 1024 * 1024) {
                throw new IllegalArgumentException(
                        "Image file size must not exceed 20MB."
                );
            }
        }

        return productRepository.save(product);
    }

    /**
     * Updates an existing product.
     *
     * If imageFile is supplied, it is validated but is not stored
     * inside the database.
     */
    public Optional<Product> updateProduct(
            Long id,
            Product productDetails,
            MultipartFile imageFile) {

        return productRepository.findById(id)
                .map(existingProduct -> {

                    // Validate name
                    if (!StringUtils.hasText(productDetails.getName())) {
                        throw new IllegalArgumentException(
                                "Product name cannot be empty."
                        );
                    }

                    // Validate description
                    if (!StringUtils.hasText(productDetails.getDescription())) {
                        throw new IllegalArgumentException(
                                "Product description cannot be empty."
                        );
                    }

                    // Validate price
                    if (productDetails.getPrice() == null
                            || productDetails.getPrice() <= 0) {

                        throw new IllegalArgumentException(
                                "Updated product price must be positive."
                        );
                    }

                    // Validate stock
                    if (productDetails.getStock() == null
                            || productDetails.getStock() < 0) {

                        throw new IllegalArgumentException(
                                "Updated product stock cannot be negative."
                        );
                    }

                    // Update normal product fields
                    existingProduct.setName(productDetails.getName());
                    existingProduct.setDescription(productDetails.getDescription());
                    existingProduct.setPrice(productDetails.getPrice());
                    existingProduct.setStock(productDetails.getStock());

                    /*
                     * Image handling:
                     *
                     * We no longer save image bytes to MySQL.
                     *
                     * The imageUrl supplied with productDetails is preserved.
                     */
                    if (productDetails.getImageUrl() != null) {
                        existingProduct.setImageUrl(
                                productDetails.getImageUrl()
                        );
                    }

                    // Validate uploaded image if one was supplied
                    if (imageFile != null && !imageFile.isEmpty()) {

                        if (imageFile.getSize() > 20 * 1024 * 1024) {
                            throw new IllegalArgumentException(
                                    "Image file size must not exceed 20MB."
                            );
                        }
                    }

                    return productRepository.save(existingProduct);
                });
    }

    /**
     * Partially updates an existing product.
     */
    public Optional<Product> updateProductPartial(
            Long id,
            Product updates) {

        return productRepository.findById(id)
                .map(existingProduct -> {

                    boolean hasUpdates = false;

                    // Update name
                    if (updates.getName() != null) {

                        if (!StringUtils.hasText(updates.getName())) {
                            throw new IllegalArgumentException(
                                    "Product name cannot be empty."
                            );
                        }

                        existingProduct.setName(updates.getName());
                        hasUpdates = true;
                    }

                    // Update description
                    if (updates.getDescription() != null) {

                        if (!StringUtils.hasText(updates.getDescription())) {
                            throw new IllegalArgumentException(
                                    "Product description cannot be empty."
                            );
                        }

                        existingProduct.setDescription(
                                updates.getDescription()
                        );

                        hasUpdates = true;
                    }

                    // Update price
                    if (updates.getPrice() != null) {

                        if (updates.getPrice() <= 0) {
                            throw new IllegalArgumentException(
                                    "Updated product price must be positive."
                            );
                        }

                        existingProduct.setPrice(updates.getPrice());
                        hasUpdates = true;
                    }

                    // Update stock
                    if (updates.getStock() != null) {

                        if (updates.getStock() < 0) {
                            throw new IllegalArgumentException(
                                    "Updated product stock cannot be negative."
                            );
                        }

                        existingProduct.setStock(updates.getStock());
                        hasUpdates = true;
                    }

                    // Update image URL
                    if (updates.getImageUrl() != null) {

                        existingProduct.setImageUrl(
                                updates.getImageUrl()
                        );

                        hasUpdates = true;
                    }

                    if (!hasUpdates) {
                        throw new IllegalArgumentException(
                                "No updatable fields provided."
                        );
                    }

                    return productRepository.save(existingProduct);
                });
    }

    /**
     * Deletes a product by its ID.
     */
    public boolean deleteProduct(Long id) {

        Optional<Product> productOptional =
                productRepository.findById(id);

        if (productOptional.isPresent()) {

            productRepository.deleteById(id);

            return true;
        }

        return false;
    }

    /**
     * Decreases stock atomically to avoid overselling.
     */
    public boolean decreaseStock(
            Long productId,
            int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }

        int updatedRows =
                productRepository.decrementStockIfAvailable(
                        productId,
                        quantity
                );

        return updatedRows == 1;
    }

    /**
     * Atomically increases stock when an order is cancelled
     * or deleted.
     */
    public boolean increaseStock(
            Long productId,
            int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }

        int updatedRows =
                productRepository.incrementStock(
                        productId,
                        quantity
                );

        return updatedRows == 1;
    }

    /**
     * Converts the database Product into the response Product.
     *
     * IMPORTANT:
     * No image bytes are copied here anymore.
     *
     * Only imageUrl is returned.
     */
    private Product toResponseProduct(
            Product source,
            ReviewRepository.ProductReviewSummaryProjection reviewSummary) {

        boolean hasRequiredExtraShortage =
                productExtraRequirementRepository
                        .hasInsufficientRequiredExtra(source.getId());

        boolean availableByOwnStock =
                source.getStock() != null
                        && source.getStock() > 0;

        boolean available =
                availableByOwnStock
                        && !hasRequiredExtraShortage;

        double averageRating = 0.0;
        long totalReviews = 0L;

        if (reviewSummary != null) {

            averageRating =
                    reviewSummary.getAverageRating() == null
                            ? 0.0
                            : reviewSummary.getAverageRating();

            totalReviews =
                    reviewSummary.getTotalReviews() == null
                            ? 0L
                            : reviewSummary.getTotalReviews();
        }

        Product response = Product.builder()
                .id(source.getId())
                .name(source.getName())
                .description(source.getDescription())
                .price(source.getPrice())
                .imageUrl(source.getImageUrl())
                .stock(source.getStock())
                .version(source.getVersion())
                .build();

        response.setAvailable(available);

        response.setEffectiveStock(
                available
                        ? source.getStock()
                        : 0
        );

        response.setAverageRating(
                Math.round(averageRating * 10.0) / 10.0
        );

        response.setTotalReviews(totalReviews);

        return response;
    }

    /**
     * Gets review summaries for all products in one query.
     */
    private Map<Long, ReviewRepository.ProductReviewSummaryProjection>
    getReviewSummaryByProductId(List<Product> products) {

        if (products == null || products.isEmpty()) {
            return Map.of();
        }

        List<Long> productIds =
                products.stream()
                        .map(Product::getId)
                        .toList();

        return reviewRepository
                .findReviewSummariesByProductIds(productIds)
                .stream()
                .collect(
                        Collectors.toMap(
                                ReviewRepository.ProductReviewSummaryProjection::getProductId,
                                summary -> summary
                        )
                );
    }
}