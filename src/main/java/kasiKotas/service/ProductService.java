package kasiKotas.service;

import kasiKotas.model.Product;
import kasiKotas.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException; // Keep this import
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product, MultipartFile imageFile) { // Removed 'throws IOException' from signature
        validateProduct(product);

        if (imageFile != null && !imageFile.isEmpty()) {
            try { // Add try-catch block here
                product.setImageData(imageFile.getBytes()); // This line can throw IOException
                product.setImageContentType(imageFile.getContentType());
                product.setImageName(imageFile.getOriginalFilename());
            } catch (IOException e) {
                // Wrap the IOException in an IllegalArgumentException (or a custom runtime exception)
                // This makes it a runtime exception, so it doesn't need to be declared.
                throw new IllegalArgumentException("Failed to read image file data: " + e.getMessage(), e);
            }
        }

        return productRepository.save(product);
    }

    public Optional<Product> updateProduct(Long id, Product productDetails, MultipartFile imageFile) { // Removed 'throws IOException' from signature
        return productRepository.findById(id).map(existingProduct -> {
            validateProduct(productDetails);

            existingProduct.setName(productDetails.getName());
            existingProduct.setDescription(productDetails.getDescription());
            existingProduct.setPrice(productDetails.getPrice());
            existingProduct.setStock(productDetails.getStock());

            if (imageFile != null && !imageFile.isEmpty()) {
                try { // Add try-catch block here
                    existingProduct.setImageData(imageFile.getBytes()); // This line can throw IOException
                    existingProduct.setImageContentType(imageFile.getContentType());
                    existingProduct.setImageName(imageFile.getOriginalFilename());
                } catch (IOException e) {
                    // Wrap the IOException in an IllegalArgumentException (or a custom runtime exception)
                    throw new IllegalArgumentException("Failed to read image file data for update: " + e.getMessage(), e);
                }
            } else if (imageFile != null && imageFile.isEmpty()) {
                existingProduct.setImageData(null);
                existingProduct.setImageContentType(null);
                existingProduct.setImageName(null);
            }
            return productRepository.save(existingProduct);
        });
    }

    public boolean deleteProduct(Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Optional<Product> decreaseStock(Long productId, int quantity) {
        return productRepository.findById(productId).map(product -> {
            if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative.");
            if (product.getStock() >= quantity) {
                product.setStock(product.getStock() - quantity);
                return productRepository.save(product);
            }
            return null;
        });
    }

    public Optional<byte[]> getImageData(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getImageData);
    }

    public Optional<String> getImageContentType(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getImageContentType);
    }

    public Optional<String> getImageName(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getImageName);
    }

    private void validateProduct(Product product) {
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
    }
}