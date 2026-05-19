// src/main/java/kasiKotas/model/BankDetails.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.util.StringUtils;

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

    // Security: Checksums for detecting tampering
    @Column(name = "account_number_checksum")
    private String accountNumberChecksum; // SHA-256 hash of account number for tamper detection

    @Column(name = "account_name_checksum")
    private String accountNameChecksum; // SHA-256 hash of account name for tamper detection

    @Column(name = "bank_name_checksum")
    private String bankNameChecksum; // SHA-256 hash of bank name for tamper detection

    // Security: Last verified timestamp
    @Column(name = "last_verified_at")
    private java.time.LocalDateTime lastVerifiedAt;

    @Column(name = "is_archived")
    private Boolean isArchived = false; // Soft-delete flag to preserve audit trail

    @Version
    private Long version;

    // Additional fields if necessary, e.g., swift code, physical address of bank

    /**
     * Checks if the essential bank details are valid (not null or empty).
     * @return true if essential details are valid, false otherwise.
     */
    public boolean isValid() {
        return StringUtils.hasText(this.bankName) &&
               StringUtils.hasText(this.accountName) &&
               StringUtils.hasText(this.accountNumber) &&
               StringUtils.hasText(this.branchCode);
    }
}
