// src/main/java/kasiKotas/service/BankDetailsService.java
package kasiKotas.service;

import kasiKotas.model.BankDetails;
import kasiKotas.repository.BankDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service layer for managing EFT bank details.
 * Supports up to two configured accounts and random selection for EFT routing.
 */
@Service
@Transactional // Ensures methods are transactional
public class BankDetailsService {

    private static final int MAX_EFT_ACCOUNTS = 2;

    private final BankDetailsRepository bankDetailsRepository;

    @Autowired
    public BankDetailsService(BankDetailsRepository bankDetailsRepository) {
        this.bankDetailsRepository = bankDetailsRepository;
    }

    // Backward-compatible getter used by existing callers.
    public Optional<BankDetails> getBankDetails() {
        List<BankDetails> allDetails = bankDetailsRepository.findAll();
        return allDetails.isEmpty() ? Optional.empty() : Optional.of(allDetails.get(0));
    }

    public List<BankDetails> getAllBankDetails() {
        return new ArrayList<>(bankDetailsRepository.findAll());
    }

    public Optional<BankDetails> getRandomEftBankDetails() {
        List<BankDetails> allDetails = bankDetailsRepository.findAll();
        if (allDetails.isEmpty()) {
            return Optional.empty();
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(allDetails.size());
        return Optional.of(allDetails.get(randomIndex));
    }

    public BankDetails saveOrUpdateBankDetails(BankDetails bankDetails) {
        if (!StringUtils.hasText(bankDetails.getBankName()) ||
                !StringUtils.hasText(bankDetails.getAccountName()) ||
                !StringUtils.hasText(bankDetails.getAccountNumber()) ||
                !StringUtils.hasText(bankDetails.getShapId()) ||
                !StringUtils.hasText(bankDetails.getBranchCode())) {
            throw new IllegalArgumentException("All bank details fields are required.");
        }

        Optional<BankDetails> accountNumberOwner = bankDetailsRepository.findByAccountNumber(bankDetails.getAccountNumber());
        if (accountNumberOwner.isPresent()) {
            if (bankDetails.getId() != null && !accountNumberOwner.get().getId().equals(bankDetails.getId())) {
                throw new IllegalArgumentException("Account number already exists for another EFT account.");
            }

            BankDetails detailsToUpdate = accountNumberOwner.get();
            detailsToUpdate.setBankName(bankDetails.getBankName());
            detailsToUpdate.setAccountName(bankDetails.getAccountName());
            detailsToUpdate.setAccountNumber(bankDetails.getAccountNumber());
            detailsToUpdate.setShapId(bankDetails.getShapId());
            detailsToUpdate.setBranchCode(bankDetails.getBranchCode());
            return bankDetailsRepository.save(detailsToUpdate);
        }

        if (bankDetails.getId() != null) {
            Optional<BankDetails> existingDetails = bankDetailsRepository.findById(bankDetails.getId());
            if (existingDetails.isPresent()) {
                BankDetails detailsToUpdate = existingDetails.get();
                detailsToUpdate.setBankName(bankDetails.getBankName());
                detailsToUpdate.setAccountName(bankDetails.getAccountName());
                detailsToUpdate.setAccountNumber(bankDetails.getAccountNumber());
                detailsToUpdate.setShapId(bankDetails.getShapId());
                detailsToUpdate.setBranchCode(bankDetails.getBranchCode());
                return bankDetailsRepository.save(detailsToUpdate);
            }
            throw new IllegalArgumentException("Bank details not found for ID: " + bankDetails.getId());
        }

        long accountCount = bankDetailsRepository.count();
        if (accountCount >= MAX_EFT_ACCOUNTS) {
            throw new IllegalArgumentException("You can only configure up to " + MAX_EFT_ACCOUNTS + " EFT accounts.");
        }

        return bankDetailsRepository.save(bankDetails);
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
