package kasiKotas.controller;

import kasiKotas.model.Product;
import kasiKotas.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders; // Import HttpHeaders for setting content type and length
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Import MediaType for image content types
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Keep if you still use security
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException; // Import IOException
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController implements WebMvcConfigurer {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        // When fetching products, the 'imageData' byte array will also be fetched.
        // Be mindful of the payload size if you fetch all products with images.
        // For efficiency, you might consider a separate DTO if the full image data
        // isn't needed for the initial listing, but for 6 images it's likely fine.
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // @PreAuthorize("hasRole('ADMIN')") // Removed based on previous discussion if you want public access
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Product> createProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        try {
            Product product = new Product();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setStock(stock);

            // The productService.createProduct method now can throw IllegalArgumentException if image processing fails
            Product createdProduct = productService.createProduct(product, imageFile);
            return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) { // Catch IllegalArgumentException for validation and image processing errors
            System.err.println("Error creating product: " + e.getMessage());
            return ResponseEntity.badRequest().body(null); // Return 400 for bad requests
        } catch (Exception e) { // Catch any other unexpected exceptions
            System.err.println("An unexpected error occurred during product creation: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Return 500 for unexpected errors
        }
    }

    @PreAuthorize("hasRole('ADMIN')") // Keeping @PreAuthorize as it was in your provided code
    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock, // CORRECTED LINE: Changed 'Integer Integer' to 'Integer stock'
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile
    ) {
        try {
            Product productDetails = new Product();
            productDetails.setName(name);
            productDetails.setDescription(description);
            productDetails.setPrice(price);
            productDetails.setStock(stock);

            // The productService.updateProduct method now can throw IllegalArgumentException if image processing fails
            return productService.updateProduct(id, productDetails, imageFile)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) { // Catch IllegalArgumentException for validation and image processing errors
            System.err.println("Error updating product: " + e.getMessage());
            return ResponseEntity.badRequest().body(null); // Return 400 for bad requests
        } catch (Exception e) { // Catch any other unexpected exceptions
            System.err.println("An unexpected error occurred during product update: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Return 500 for unexpected errors
        }
    }

    @PreAuthorize("hasRole('ADMIN')") // Keeping @PreAuthorize as it was in your provided code
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteProduct(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // --- NEW ENDPOINT TO SERVE IMAGE FROM DATABASE ---
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getProductImage(@PathVariable Long id) {
        Optional<byte[]> imageDataOptional = productService.getImageData(id);
        Optional<String> contentTypeOptional = productService.getImageContentType(id);

        if (imageDataOptional.isPresent() && contentTypeOptional.isPresent()) {
            byte[] imageData = imageDataOptional.get();
            String contentType = contentTypeOptional.get();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(imageData.length); // Good practice to set content length

            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
        } else {
            // Option 1: Return 404 if no image or product found
            return ResponseEntity.notFound().build();
            // Option 2: Return a default placeholder image if no image found for product
            // Example: return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(defaultImageBytes);
        }
    }
    // --- END NEW IMAGE ENDPOINT ---

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // This method is for serving static files from the file system.
        // It's not directly needed for serving images from the database,
        // but can remain if you serve other static content.
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations("file:./uploads/product-images/");
    }
}