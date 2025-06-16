// src/main/java/kasiKotas/controller/ProductController.java
package kasiKotas.controller;

import kasiKotas.model.Product;
import kasiKotas.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // For handling file uploads
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * REST Controller for managing Product (Kota) related operations.
 * Exposes API endpoints for retrieving, adding, updating, and deleting products.
 * Now supports image uploads for products.
 */
@RestController // Marks this class as a REST controller
@RequestMapping("/api/products") // Base path for all endpoints in this controller
public class ProductController implements WebMvcConfigurer { // Implement WebMvcConfigurer for resource handlers

    private final ProductService productService; // Inject ProductService

    // Constructor injection for dependencies
    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Retrieves a list of all products.
     * GET /api/products
     * @return A ResponseEntity containing a list of Product objects and HTTP status 200 OK.
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products); // Returns 200 OK with the list of products
    }

    /**
     * Retrieves a single product by its ID.
     * GET /api/products/{id}
     * @param id The ID of the product to retrieve.
     * @return A ResponseEntity containing the Product if found (200 OK), or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok) // If product found, return 200 OK with product
                .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found
    }

    /**
     * Creates a new product, now accepting a MultipartFile for the image.
     * POST /api/products
     * Content-Type: multipart/form-data
     * @param name The name of the product.
     * @param description The description of the product.
     * @param price The price of the product.
     * @param stock The stock quantity of the product.
     * @param imageFile The uploaded image file (optional).
     * @return A ResponseEntity containing the created Product and 201 Created status,
     * or 400 Bad Request if validation fails.
     */
    @PostMapping(consumes = {"multipart/form-data"}) // Consumes multipart/form-data
    public ResponseEntity<Product> createProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile // Optional file part
    ) {
        try {
            Product product = new Product();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setStock(stock);
            // imageUrl will be set by service if imageFile is provided

            Product createdProduct = productService.createProduct(product, imageFile);
            return new ResponseEntity<>(createdProduct, HttpStatus.CREATED); // Returns 201 Created
        } catch (IllegalArgumentException e) {
            // Catches validation errors or image storage errors from the service layer
            System.err.println("Error creating product: " + e.getMessage()); // Log error for debugging
            return ResponseEntity.badRequest().build(); // Returns 400 Bad Request
        }
    }

    /**
     * Updates an existing product, now with optional image upload.
     * PUT /api/products/{id}
     * Content-Type: multipart/form-data
     * @param id The ID of the product to update.
     * @param name The name of the product.
     * @param description The description of the product.
     * @param price The price of the product.
     * @param stock The stock quantity of the product.
     * @param imageFile The uploaded image file (optional).
     * @return A ResponseEntity containing the updated Product (200 OK), 404 Not Found,
     * or 400 Bad Request if validation fails.
     */
    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        try {
            Product productDetails = new Product();
            productDetails.setName(name);
            productDetails.setDescription(description);
            productDetails.setPrice(price);
            productDetails.setStock(stock);

            // Note: If you also want to support updating imageUrl string directly without a file upload
            // you'd need to add @RequestParam("imageUrl") String imageUrl and set productDetails.setImageUrl(imageUrl)
            // if imageFile is null. For now, it only supports file upload or keeping existing.


            return productService.updateProduct(id, productDetails, imageFile)
                    .map(ResponseEntity::ok) // If product updated, return 200 OK
                    .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found
        } catch (IllegalArgumentException e) {
            // Catches validation errors from the service layer
            System.err.println("Error updating product: " + e.getMessage()); // Log error for debugging
            return ResponseEntity.badRequest().build(); // Returns 400 Bad Request
        }
    }

    /**
     * Deletes a product by its ID.
     * DELETE /api/products/{id}
     * @param id The ID of the product to delete.
     * @return A ResponseEntity with 204 No Content if successful, or 404 Not Found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteProduct(id);
        if (deleted) {
            return ResponseEntity.noContent().build(); // Returns 204 No Content on successful deletion
        } else {
            return ResponseEntity.notFound().build(); // Returns 404 Not Found if product not found
        }
    }

    /**
     * Configures a resource handler to serve static content (product images)
     * from the 'uploads/product-images' directory.
     * Images will be accessible via URLs like http://localhost:8080/product-images/your_image.png
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // This maps requests starting with "/product-images/" to the local file system path.
        // "file:" prefix is crucial for accessing local files.
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations("file:./uploads/product-images/"); // Maps to the folder created by ProductService
    }
}
