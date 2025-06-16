// src/main/java/kasiKotas/service/ProductService.java
package kasiKotas.service;

import kasiKotas.model.Product;
import kasiKotas.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile; // For handling file uploads

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID; // For generating unique filenames

/**
 * Service layer for managing Product (Kota) related business logic.
 * This class orchestrates operations between the Controller and the Repository.
 * It's now updated to handle image uploads for products.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    // Define the upload directory for product images
    // IMPORTANT: In a real application, this should be outside the compiled JAR/WAR
    // and ideally on a dedicated file storage solution (e.g., S3, GCS).
    // For local development, we'll create a folder in the project root or a temp dir.
    // Ensure this directory exists and is writable by the application.
    private final Path imageStorageLocation = Paths.get("uploads/product-images").toAbsolutePath().normalize();

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
        // Create the directory if it doesn't exist when the service is initialized
        try {
            Files.createDirectories(this.imageStorageLocation);
            System.out.println("Image storage directory created at: " + this.imageStorageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create image storage directory!", e);
        }
    }

    /**
     * Retrieves all products from the database.
     * @return A list of all Product objects.
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Retrieves a product by its ID.
     * @param id The ID of the product to retrieve.
     * @return An Optional containing the Product if found, or empty if not found.
     */
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Creates a new product, now including an option for image upload.
     * If an image file is provided, it will be saved to disk and its path stored.
     * @param product The Product object to save (contains name, description, price, stock).
     * @param imageFile The MultipartFile representing the uploaded image (can be null).
     * @return The saved Product object.
     * @throws IllegalArgumentException if product details are invalid or image upload fails.
     */
    public Product createProduct(Product product, MultipartFile imageFile) {
        // --- Business logic and validation for product details ---
        if (!StringUtils.hasText(product.getName())) {
            throw new IllegalArgumentException("Product name cannot be empty.");
        }
        if (!StringUtils.hasText(product.getDescription())) {
            throw new IllegalArgumentException("Product description cannot be empty.");
        }
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new IllegalArgumentException("Product price must be positive.");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative.");
        }
        // -----------------------------------------------------------------

        // Handle image upload if a file is provided
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String fileName = saveProductImage(imageFile);
                product.setImageUrl("/product-images/" + fileName); // Store a relative URL for access
            } catch (IOException e) {
                // If image saving fails, throw an exception or handle it
                throw new IllegalArgumentException("Failed to store image: " + e.getMessage(), e);
            }
        } else if (!StringUtils.hasText(product.getImageUrl())) {
            // If no file and no imageUrl provided, set a default/placeholder
            product.setImageUrl("/product-images/default.png"); // Consider a default image if no upload
        }
        // If imageUrl was provided directly AND no imageFile, it will just use the provided URL.

        return productRepository.save(product);
    }

    /**
     * Updates an existing product, now with optional image upload.
     * If a new image file is provided, it replaces the old one.
     * If imageFile is null, the existing imageUrl is retained.
     * @param id The ID of the product to update.
     * @param productDetails The updated Product object.
     * @param imageFile The new image file (can be null).
     * @return An Optional containing the updated Product if found, or empty if not found.
     * @throws IllegalArgumentException if product details are invalid or image upload fails.
     */
    public Optional<Product> updateProduct(Long id, Product productDetails, MultipartFile imageFile) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    // Apply the same validation rules for updates
                    if (!StringUtils.hasText(productDetails.getName())) {
                        throw new IllegalArgumentException("Product name cannot be empty.");
                    }
                    if (!StringUtils.hasText(productDetails.getDescription())) {
                        throw new IllegalArgumentException("Product description cannot be empty.");
                    }
                    if (productDetails.getPrice() == null || productDetails.getPrice() <= 0) {
                        throw new IllegalArgumentException("Updated product price must be positive.");
                    }
                    if (productDetails.getStock() == null || productDetails.getStock() < 0) {
                        throw new IllegalArgumentException("Updated product stock cannot be negative.");
                    }

                    // Handle image update
                    if (imageFile != null && !imageFile.isEmpty()) {
                        try {
                            // Delete old image if it exists (optional, depends on cleanup strategy)
                            // deleteProductImage(existingProduct.getImageUrl());
                            String fileName = saveProductImage(imageFile);
                            existingProduct.setImageUrl("/product-images/" + fileName);
                        } catch (IOException e) {
                            throw new IllegalArgumentException("Failed to update image: " + e.getMessage(), e);
                        }
                    } else if (StringUtils.hasText(productDetails.getImageUrl())) {
                        // If a new URL is provided (and no file), update it
                        existingProduct.setImageUrl(productDetails.getImageUrl());
                    } else if (!StringUtils.hasText(productDetails.getImageUrl()) && !StringUtils.hasText(existingProduct.getImageUrl())) {
                        // If no new image and no existing image, set to default/null
                        existingProduct.setImageUrl("/product-images/default.png");
                    }
                    // If imageFile is null and productDetails.getImageUrl() is null/empty,
                    // and existingProduct.getImageUrl() had a value, it remains unchanged by default.
                    // This logic depends on whether 'null' in payload should clear existing image.


                    // Update other fields
                    existingProduct.setName(productDetails.getName());
                    existingProduct.setDescription(productDetails.getDescription());
                    existingProduct.setPrice(productDetails.getPrice());
                    existingProduct.setStock(productDetails.getStock());

                    return productRepository.save(existingProduct); // Save the updated product
                });
    }


    /**
     * Deletes a product by its ID.
     * @param id The ID of the product to delete.
     * @return true if the product was found and deleted, false otherwise.
     */
    public boolean deleteProduct(Long id) {
        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isPresent()) {
            Product product = productOptional.get();
            // Optional: Delete the associated image file from storage
            // deleteProductImage(product.getImageUrl());
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Decreases the stock of a product.
     * @param productId The ID of the product.
     * @param quantity The quantity to deduct from stock.
     * @return An Optional containing the updated Product, or empty if product not found or insufficient stock.
     */
    public Optional<Product> decreaseStock(Long productId, int quantity) {
        return productRepository.findById(productId)
                .map(product -> {
                    if (quantity < 0) {
                        throw new IllegalArgumentException("Quantity to decrease cannot be negative.");
                    }
                    if (product.getStock() >= quantity) {
                        product.setStock(product.getStock() - quantity);
                        return productRepository.save(product);
                    } else {
                        // In a real application, you might throw a custom exception here
                        System.err.println("Insufficient stock for product ID: " + productId);
                        return null; // Indicate failure due to insufficient stock
                    }
                });
    }

    /**
     * Saves an uploaded product image to the configured storage location.
     * Generates a unique filename to prevent collisions.
     * @param file The MultipartFile received from the client.
     * @return The unique filename generated and saved.
     * @throws IOException If there's an error saving the file.
     * @throws IllegalArgumentException If the file is empty or filename is invalid.
     */
    private String saveProductImage(MultipartFile file) throws IOException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("Filename contains invalid path sequence " + originalFileName);
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file " + originalFileName);
        }

        // Generate a unique filename to prevent overwriting existing files
        String fileExtension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFileName.length() - 1) {
            fileExtension = originalFileName.substring(dotIndex);
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path targetLocation = this.imageStorageLocation.resolve(uniqueFileName);

        // Copy file to the target location, replacing existing file if it has the same name
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return uniqueFileName;
    }

    /**
     * Optional: Deletes a product image from storage based on its relative URL.
     * This method would be called when a product is deleted or its image is updated.
     * @param imageUrl The relative URL of the image (e.g., "/product-images/unique-id.png").
     */
    private void deleteProductImage(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("/product-images/")) {
            String fileName = imageUrl.substring("/product-images/".length());
            Path filePath = this.imageStorageLocation.resolve(fileName);
            try {
                Files.deleteIfExists(filePath);
                System.out.println("Deleted image: " + filePath);
            } catch (IOException e) {
                System.err.println("Could not delete image file: " + filePath + " - " + e.getMessage());
                // Log the error, but don't prevent the main operation (product deletion/update)
            }
        }
    }
}
