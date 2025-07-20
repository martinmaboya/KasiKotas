
        package kasiKotas.controller;

        import kasiKotas.model.Product;
        import kasiKotas.service.ProductService;
        import org.springframework.beans.factory.annotation.Autowired;
        import org.springframework.http.HttpStatus;
        import org.springframework.http.ResponseEntity;
        import org.springframework.security.access.prepost.PreAuthorize;
        import org.springframework.web.bind.annotation.*;
        import org.springframework.web.multipart.MultipartFile;
        import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
        import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

        import java.util.List;

        @RestController
        @RequestMapping("/api/products")
        public class ProductController implements WebMvcConfigurer {

            private final ProductService productService;

            @Autowired
            public ProductController(ProductService productService) {
                this.productService = productService;
            }

            @GetMapping("/get")
            public ResponseEntity<List<Product>> getAllProducts() {
                List<Product> products = productService.getAllProducts();
                return ResponseEntity.ok(products);
            }

            @GetMapping("/{id}")
            public ResponseEntity<Product> getProductById(@PathVariable Long id) {
                return productService.getProductById(id)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build());
            }

            @PreAuthorize("hasRole('ADMIN')")
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

                    Product createdProduct = productService.createProduct(product, imageFile);
                    return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error creating product: " + e.getMessage());
                    return ResponseEntity.badRequest().build();
                }
            }

            @PreAuthorize("hasRole('ADMIN')")
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

                    return productService.updateProduct(id, productDetails, imageFile)
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build());
                } catch (IllegalArgumentException e) {
                    System.err.println("Error updating product: " + e.getMessage());
                    return ResponseEntity.badRequest().build();
                }
            }

            @PreAuthorize("hasRole('ADMIN')")
            @DeleteMapping("/{id}")
            public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
                boolean deleted = productService.deleteProduct(id);
                if (deleted) {
                    return ResponseEntity.noContent().build();
                } else {
                    return ResponseEntity.notFound().build();
                }
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/product-images/**")
                        .addResourceLocations("file:./uploads/product-images/");
            }
        }