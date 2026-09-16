package kasiKotas.controller;

import kasiKotas.model.Product;
import kasiKotas.service.ProductService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all products.
     *
     * Product information is returned without image binary data.
     * The frontend uses imageUrl to load the actual image.
     */
    @GetMapping("/get-all")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * Get a single product by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(
            @PathVariable Long id) {

        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a new product.
     *
     * The image file is accepted for compatibility with the existing
     * frontend/admin form. It is NOT stored as a database BLOB.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Product> createProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestPart(value = "imageFile", required = false)
            MultipartFile imageFile
    ) {

        try {

            Product product = new Product();

            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setStock(stock);

            Product createdProduct =
                    productService.createProduct(
                            product,
                            imageFile
                    );

            return new ResponseEntity<>(
                    createdProduct,
                    HttpStatus.CREATED
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().build();

        } catch (OptimisticLockingFailureException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }
    }

    /**
     * Update an existing product.
     *
     * The image file is accepted for compatibility but is not stored
     * inside the database.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(
            value = "/{id}",
            consumes = {"multipart/form-data"}
    )
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestPart(value = "imageFile", required = false)
            MultipartFile imageFile
    ) {

        try {

            Product productDetails = new Product();

            productDetails.setName(name);
            productDetails.setDescription(description);
            productDetails.setPrice(price);
            productDetails.setStock(stock);

            return productService
                    .updateProduct(
                            id,
                            productDetails,
                            imageFile
                    )
                    .map(ResponseEntity::ok)
                    .orElseGet(
                            () -> ResponseEntity
                                    .notFound()
                                    .build()
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().build();

        } catch (OptimisticLockingFailureException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }
    }

    /**
     * Partial JSON update.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(
            value = "/{id}",
            consumes = {"application/json"}
    )
    public ResponseEntity<Product> updateProductJson(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {

        try {

            Product updates = new Product();

            if (payload.containsKey("name")) {
                updates.setName(
                        optionalString(payload, "name")
                );
            }

            if (payload.containsKey("description")) {
                updates.setDescription(
                        optionalString(payload, "description")
                );
            }

            if (payload.containsKey("price")) {
                updates.setPrice(
                        optionalDouble(payload, "price")
                );
            }

            if (payload.containsKey("stock")) {
                updates.setStock(
                        optionalInteger(payload, "stock")
                );
            }

            if (payload.containsKey("imageUrl")) {
                updates.setImageUrl(
                        optionalString(payload, "imageUrl")
                );
            }

            return productService
                    .updateProductPartial(id, updates)
                    .map(ResponseEntity::ok)
                    .orElseGet(
                            () -> ResponseEntity
                                    .notFound()
                                    .build()
                    );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().build();

        } catch (OptimisticLockingFailureException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }
    }

    /**
     * Delete a product.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id) {

        boolean deleted =
                productService.deleteProduct(id);

        if (deleted) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Safely reads a String from a JSON payload.
     */
    private String optionalString(
            Map<String, Object> payload,
            String key) {

        Object value = payload.get(key);

        if (value == null) {
            return null;
        }

        return value.toString();
    }

    /**
     * Safely reads a Double from a JSON payload.
     */
    private Double optionalDouble(
            Map<String, Object> payload,
            String key) {

        Object value = payload.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }

        if (value instanceof String stringValue
                && !stringValue.isBlank()) {

            return Double.parseDouble(
                    stringValue.trim()
            );
        }

        throw new IllegalArgumentException(
                "Invalid " + key + " format."
        );
    }

    /**
     * Safely reads an Integer from a JSON payload.
     */
    private Integer optionalInteger(
            Map<String, Object> payload,
            String key) {

        Object value = payload.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }

        if (value instanceof String stringValue
                && !stringValue.isBlank()) {

            return Integer.parseInt(
                    stringValue.trim()
            );
        }

        throw new IllegalArgumentException(
                "Invalid " + key + " format."
        );
    }
}