// src/main/java/kasiKotas/model/BankDetails.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents the banking details for the business,
 * which will be displayed for EFT payments.
 * This is a JPA Entity, mapping to a table in the database.
 * We anticipate only one entry for these details.
 */
@Entity
@Table(name = "bank_details") // Specifies the table name in the database
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode methods automatically
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder
public class BankDetails {

    // Using a fixed ID or finding the existing one.
    // In a system where there's only one instance of something,
    // a fixed ID (e.g., 1L) can be used to always fetch/update it.
    // Or, we can use GeneratedValue for simplicity and just ensure only one is created via logic.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Primary key for the bank_details table

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false, unique = true) // Account number should likely be unique
    private String accountNumber;

    @Column // Moved shapId here, after accountNumber. It's optional (nullable by default).
    private String shapId;

    @Column(nullable = false)
    private String branchCode;

    // Additional fields if necessary, e.g., swift code, physical address of bank
}
