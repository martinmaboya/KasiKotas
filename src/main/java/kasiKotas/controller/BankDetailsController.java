package kasiKotas.controller;

import kasiKotas.model.BankDetails;
import kasiKotas.model.BankDetailsAudit;
import kasiKotas.service.BankDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * REST Controller for managing business banking details.
 * Only users with ADMIN role can access these endpoints.
 */
@RestController
@RequestMapping("/api/bank-details")
public class BankDetailsController {

    private static final Logger log = LoggerFactory.getLogger(BankDetailsController.class);

    private final BankDetailsService bankDetailsService;

    @Autowired
    public BankDetailsController(BankDetailsService bankDetailsService) {
        this.bankDetailsService = bankDetailsService;
    }

    /**
     * Retrieves the business bank details.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<BankDetails> getBankDetails() {
        return bankDetailsService.getBankDetails()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<?> getAllBankDetails() {
        return ResponseEntity.ok(bankDetailsService.getAllBankDetails());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('CUSTOMER')")
    @GetMapping("/eft")
    public ResponseEntity<BankDetails> getRandomEftBankDetails() {
        return bankDetailsService.getRandomEftBankDetails()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates or updates the business bank details.
     * Only accessible by ADMIN users.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BankDetails> saveOrUpdateBankDetails(@RequestBody BankDetails bankDetails) {
        try {
            BankDetails savedDetails = bankDetailsService.saveOrUpdateBankDetails(bankDetails);
            return ResponseEntity.ok(savedDetails);
        } catch (IllegalArgumentException e) {
            log.warn("Error saving/updating bank details: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("An unexpected error occurred while saving/updating bank details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit")
    public ResponseEntity<List<BankDetailsAudit>> getAuditHistory() {
        return ResponseEntity.ok(bankDetailsService.getAuditHistory());
    }

    // Optional: Add a DELETE endpoint if needed, also protected by ADMIN role.
    // @PreAuthorize("hasRole('ADMIN')")
    // @DeleteMapping("/{id}")
    // public ResponseEntity<Void> deleteBankDetails(@PathVariable Long id) {
    //     boolean deleted = bankDetailsService.deleteBankDetails(id);
    //     if (deleted) {
    //         return ResponseEntity.noContent().build();
    //     } else {
    //         return ResponseEntity.notFound().build();
    //     }
    // }
}