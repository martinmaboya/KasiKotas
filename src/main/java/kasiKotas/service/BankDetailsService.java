// src/main/java/kasiKotas/service/BankDetailsService.java
package kasiKotas.service;

import kasiKotas.model.BankDetails;
import kasiKotas.repository.BankDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for managing business banking details.
 * This service handles retrieving and updating the bank details.
 * It's designed assuming there will be only one set of banking details.
 */
@Service
@Transactional // Ensures methods are transactional
public class BankDetailsService {

    private final BankDetailsRepository bankDetailsRepository;

    @Autowired
    public BankDetailsService(BankDetailsRepository bankDetailsRepository) {
        this.bankDetailsRepository = bankDetailsRepository;
    }

    /**
     * Retrieves the single set of bank details.
     * Since we expect only one, it tries to find the first one or returns empty.
     * @return An Optional containing BankDetails if found, or empty.
     */
    public Optional<BankDetails> getBankDetails() {
        List<BankDetails> allDetails = bankDetailsRepository.findAll();
        // Return the first entry if it exists, as we expect only one.
        return allDetails.isEmpty() ? Optional.empty() : Optional.of(allDetails.get(0));
    }

    /**
     * Creates or updates the bank details.
     * If details already exist (e.g., ID 1), it updates them. Otherwise, it creates new ones.
     * It's designed to manage a single entry for bank details.
     * @param bankDetails The BankDetails object to save or update.
     * @return The saved/updated BankDetails object.
     * @throws IllegalArgumentException if required fields are empty.
     */
    public BankDetails saveOrUpdateBankDetails(BankDetails bankDetails) {
        // Basic validation
        if (!StringUtils.hasText(bankDetails.getBankName()) ||
                !StringUtils.hasText(bankDetails.getAccountName()) ||
                !StringUtils.hasText(bankDetails.getAccountNumber()) ||
                !StringUtils.hasText(bankDetails.getBranchCode())) {
            throw new IllegalArgumentException("All bank details fields (bank name, account name, account number, branch code) are required.");
        }

        // If an ID is provided, try to find and update it.
        // Otherwise, save as a new entry.
        // For a single entry system, you might always update the first (ID 1).
        // For now, if ID is null, it creates a new one.
        if (bankDetails.getId() != null) {
            Optional<BankDetails> existingDetails = bankDetailsRepository.findById(bankDetails.getId());
            if (existingDetails.isPresent()) {
                BankDetails detailsToUpdate = existingDetails.get();
                detailsToUpdate.setBankName(bankDetails.getBankName());
                detailsToUpdate.setAccountName(bankDetails.getAccountName());
                detailsToUpdate.setAccountNumber(bankDetails.getAccountNumber());
                detailsToUpdate.setBranchCode(bankDetails.getBranchCode());
                return bankDetailsRepository.save(detailsToUpdate);
            }
        }
        // If no ID or not found, save as new.
        // In a single-record scenario, this might create a second record.
        // A more robust solution might be to always update the first record found.
        List<BankDetails> existingBankDetails = bankDetailsRepository.findAll();
        if (!existingBankDetails.isEmpty()) {
            // If details already exist, update the first one
            BankDetails detailsToUpdate = existingBankDetails.get(0);
            detailsToUpdate.setBankName(bankDetails.getBankName());
            detailsToUpdate.setAccountName(bankDetails.getAccountName());
            detailsToUpdate.setAccountNumber(bankDetails.getAccountNumber());
            detailsToUpdate.setBranchCode(bankDetails.getBranchCode());
            return bankDetailsRepository.save(detailsToUpdate);
        } else {
            // No details exist, create a new one
            return bankDetailsRepository.save(bankDetails);
        }
    }

    // Optional: Delete bank details (usually not needed for a single config entry)
    public boolean deleteBankDetails(Long id) {
        if (bankDetailsRepository.existsById(id)) {
            bankDetailsRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
