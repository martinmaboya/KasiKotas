// src/main/java/kasiKotas/service/BankDetailsService.java
package kasiKotas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kasiKotas.model.BankDetails;
import kasiKotas.model.BankDetailsAudit;
import kasiKotas.model.BankDetailsAudit.AuditAction;
import kasiKotas.repository.BankDetailsRepository;
import kasiKotas.repository.BankDetailsAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDateTime;

/**
 * Service layer for managing EFT bank details.
 * Supports up to two configured accounts and random selection for EFT routing.
 */
@Service
@Transactional // Ensures methods are transactional
public class BankDetailsService {

    private static final int MAX_EFT_ACCOUNTS = 2;

    private final BankDetailsRepository bankDetailsRepository;
    private final BankDetailsAuditRepository bankDetailsAuditRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public BankDetailsService(BankDetailsRepository bankDetailsRepository,
                              BankDetailsAuditRepository bankDetailsAuditRepository,
                              ObjectMapper objectMapper) {
        this.bankDetailsRepository = bankDetailsRepository;
        this.bankDetailsAuditRepository = bankDetailsAuditRepository;
        this.objectMapper = objectMapper;
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
        BankDetails existingDetails = null;

        if (bankDetails.getId() != null) {
            existingDetails = bankDetailsRepository.findById(bankDetails.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Bank details not found for ID: " + bankDetails.getId()));
        } else if (accountNumberOwner.isPresent()) {
            existingDetails = accountNumberOwner.get();
        }

        if (accountNumberOwner.isPresent()) {
            if (bankDetails.getId() != null && !accountNumberOwner.get().getId().equals(bankDetails.getId())) {
                throw new IllegalArgumentException("Account number already exists for another EFT account.");
            }
        }

        if (existingDetails != null) {
            BankDetails beforeUpdate = snapshot(existingDetails);
            existingDetails.setBankName(bankDetails.getBankName());
            existingDetails.setAccountName(bankDetails.getAccountName());
            existingDetails.setAccountNumber(bankDetails.getAccountNumber());
            existingDetails.setShapId(bankDetails.getShapId());
            existingDetails.setBranchCode(bankDetails.getBranchCode());

            if (detailsAreEqual(beforeUpdate, existingDetails)) {
                return existingDetails;
            }

            BankDetails saved = bankDetailsRepository.save(existingDetails);
            writeAudit(AuditAction.UPDATE, beforeUpdate, saved);
            return saved;
        }

        long accountCount = bankDetailsRepository.count();
        if (accountCount >= MAX_EFT_ACCOUNTS) {
            throw new IllegalArgumentException("You can only configure up to " + MAX_EFT_ACCOUNTS + " EFT accounts.");
        }

        BankDetails saved = bankDetailsRepository.save(bankDetails);
        writeAudit(AuditAction.CREATE, null, saved);
        return saved;
    }

    // Optional: Delete bank details (usually not needed for a single config entry)
    public boolean deleteBankDetails(Long id) {
        Optional<BankDetails> existing = bankDetailsRepository.findById(id);
        if (existing.isPresent()) {
            BankDetails beforeDelete = snapshot(existing.get());
            bankDetailsRepository.deleteById(id);
            writeAudit(AuditAction.DELETE, beforeDelete, null);
            return true;
        }
        return false;
    }

    public List<BankDetailsAudit> getAuditHistory() {
        return bankDetailsAuditRepository.findAllByOrderByChangedAtDesc();
    }

    private void writeAudit(AuditAction action, BankDetails before, BankDetails after) {
        BankDetailsAudit audit = BankDetailsAudit.builder()
                .bankDetailsId(after != null ? after.getId() : (before != null ? before.getId() : null))
                .action(action)
                .actorUsername(resolveActorUsername())
                .changedAt(LocalDateTime.now())
                .beforeSnapshotJson(before == null ? null : toJson(before))
                .afterSnapshotJson(after == null ? null : toJson(after))
                .build();
        bankDetailsAuditRepository.save(audit);
    }

    private BankDetails snapshot(BankDetails source) {
        return BankDetails.builder()
                .id(source.getId())
                .bankName(source.getBankName())
                .accountName(source.getAccountName())
                .accountNumber(source.getAccountNumber())
                .shapId(source.getShapId())
                .branchCode(source.getBranchCode())
                .version(source.getVersion())
                .build();
    }

    private boolean detailsAreEqual(BankDetails left, BankDetails right) {
        return safeEquals(left.getBankName(), right.getBankName())
                && safeEquals(left.getAccountName(), right.getAccountName())
                && safeEquals(left.getAccountNumber(), right.getAccountNumber())
                && safeEquals(left.getShapId(), right.getShapId())
                && safeEquals(left.getBranchCode(), right.getBranchCode());
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String toJson(BankDetails bankDetails) {
        try {
            return objectMapper.writeValueAsString(bankDetails);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize bank details audit snapshot.", ex);
        }
    }

    private String resolveActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String username && StringUtils.hasText(username) && !"anonymousUser".equalsIgnoreCase(username)) {
            return username;
        }

        return authentication.getName();
    }
}
