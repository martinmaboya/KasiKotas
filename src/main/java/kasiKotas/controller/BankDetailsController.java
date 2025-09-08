package kasiKotas.controller;

import kasiKotas.model.BankDetails;
import kasiKotas.service.BankDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing business banking details.
 * Only users with ADMIN role can access these endpoints.
 */
@RestController
@RequestMapping("/api/bank-details")
public class BankDetailsController {

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
            System.err.println("Error saving/updating bank details: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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