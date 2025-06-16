// src/main/java/kasiKotas/controller/BankDetailsController.java
package kasiKotas.controller;

import kasiKotas.model.BankDetails;
import kasiKotas.service.BankDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing business banking details.
 * This controller provides endpoints for retrieving and updating
 * the single set of banking details used for EFT payments.
 *
 * IMPORTANT: In a real application, these endpoints MUST be protected
 * with Spring Security to ensure only authenticated ADMIN users can access them.
 * For now, they are publicly accessible for development purposes.
 */
@RestController
@RequestMapping("/api/bank-details") // Base path for these endpoints
public class BankDetailsController {

    private final BankDetailsService bankDetailsService;

    @Autowired
    public BankDetailsController(BankDetailsService bankDetailsService) {
        this.bankDetailsService = bankDetailsService;
    }

    /**
     * Retrieves the business bank details.
     * GET /api/bank-details
     * This endpoint will be used by the frontend to display EFT information.
     * @return ResponseEntity with BankDetails (200 OK) or 404 Not Found.
     */
    @GetMapping
    public ResponseEntity<BankDetails> getBankDetails() {
        return bankDetailsService.getBankDetails()
                .map(ResponseEntity::ok) // If found, return 200 OK with details
                .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found
    }

    /**
     * Creates or updates the business bank details.
     * POST /api/bank-details
     * This endpoint is intended for use by administrators to set up/modify bank details.
     *
     * IMPORTANT: This endpoint needs proper authentication and authorization (ADMIN role).
     * @param bankDetails The BankDetails object to be saved or updated.
     * @return ResponseEntity with the saved/updated BankDetails (200 OK or 201 Created)
     * or 400 Bad Request if validation fails.
     */
    @PostMapping
    public ResponseEntity<BankDetails> saveOrUpdateBankDetails(@RequestBody BankDetails bankDetails) {
        try {
            BankDetails savedDetails = bankDetailsService.saveOrUpdateBankDetails(bankDetails);
            // If it's a new creation, you might want to return HttpStatus.CREATED
            // If it's an update, HttpStatus.OK is fine. Since service handles both, OK is a safe default.
            return ResponseEntity.ok(savedDetails);
        } catch (IllegalArgumentException e) {
            System.err.println("Error saving/updating bank details: " + e.getMessage());
            return ResponseEntity.badRequest().build(); // Return 400 Bad Request for validation errors
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Optional: Add a DELETE endpoint if you want to allow deleting bank details,
    // though for critical configuration like this, it's usually managed via PUT/POST updates.
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
