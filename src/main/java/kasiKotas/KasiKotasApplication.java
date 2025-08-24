// src/main/java/kasiKotas/KasiKotasApplication.java
package kasiKotas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for KasiKotas.
 * This class is responsible for bootstrapping the application.
 */
@SpringBootApplication // Marks this as a Spring Boot application
@EnableScheduling // Enable scheduling for scheduled delivery processing
public class KasiKotasApplication {

    public static void main(String[] args) {
        // Run the Spring Boot application
        SpringApplication.run(KasiKotasApplication.class, args);
    }
}
