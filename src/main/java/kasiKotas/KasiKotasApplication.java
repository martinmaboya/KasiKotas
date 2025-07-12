// src/main/java/kasiKotas/KasiKotasApplication.java
package kasiKotas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Main Spring Boot application class for KasiKotas.
 * This class is responsible for bootstrapping the application.
 */
@SpringBootApplication // Marks this as a Spring Boot application
public class KasiKotasApplication {

    public static void main(String[] args) {
        // Run the Spring Boot application
        SpringApplication.run(KasiKotasApplication.class, args);
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) to allow requests
     * from your frontend application, which will likely run on a different port/origin.
     * In a production environment, you should restrict this to your specific frontend origin(s).
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // IMPORTANT: In production, replace "*" with the actual URL(s) of your deployed frontend.
                // Example: .allowedOrigins("https://your-frontend-app.onrender.com", "http://localhost:3000")
                registry.addMapping("/**") // Apply CORS to all endpoints
                        .allowedOrigins("https://kasikotas-frondend.onrender.com/") // Allow all origins (for development)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allowed HTTP methods
                        .allowedHeaders("*") // Allow all headers
                        .allowCredentials(true) // Do not allow credentials (e.g., cookies, auth headers)
                        .maxAge(3600); // Max age for preflight requests
            }
        };
    }
}
