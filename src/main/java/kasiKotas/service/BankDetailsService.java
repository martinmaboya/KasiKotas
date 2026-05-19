// src/main/java/kasiKotas/service/BankDetailsService.java
package kasiKotas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kasiKotas.model.BankDetails;
import kasiKotas.model.BankDetailsAudit;
import kasiKotas.model.BankDetailsAudit.AuditAction;
import kasiKotas.repository.BankDetailsRepository;
import kasiKotas.repository.BankDetailsAuditRepository;
import kasiKotas.security.BankDetailsEncryption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDateTime;

/**
 * Service layer for managing EFT bank details.
 * Supports up to two configured accounts and random selection for EFT routing.
 * 
 * SECURITY FEATURES:
 * - Only ADMIN users can create/modify bank details
 * - All modifications are encrypted and checksummed
 * - Complete audit trail of all changes with actor tracking
 * - Soft-delete (archival) to preserve payment history
 * - Checksums detect unauthorized tampering
 * - Account numbers are encrypted at rest
 */
@Service
@Transactional // Ensures methods are transactional
public class BankDetailsService {

    private static final int MAX_EFT_ACCOUNTS = 2;

    private final BankDetailsRepository bankDetailsRepository;
    private final BankDetailsAuditRepository bankDetailsAuditRepository;
    private final BankDetailsEncryption encryption;
    private final ObjectMapper objectMapper;

    @Autowired
    public BankDetailsService(BankDetailsRepository bankDetailsRepository,
                              BankDetailsAuditRepository bankDetailsAuditRepository,
                              BankDetailsEncryption encryption,
                              ObjectMapper objectMapper) {
        this.bankDetailsRepository = bankDetailsRepository;
        this.bankDetailsAuditRepository = bankDetailsAuditRepository;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
    }

    // Backward-compatible getter used by existing callers.
    public Optional<BankDetails> getBankDetails() {
        List<BankDetails> allDetails = bankDetailsRepository.findAll()
            .stream()
            .filter(bd -> !Boolean.TRUE.equals(bd.getIsArchived())) // Exclude archived
            .toList();
        
        if (allDetails.isEmpty()) {
            return Optional.empty();
        }
        
        BankDetails details = allDetails.get(0);
        verifyIntegrityOrThrow(details);
        return Optional.of(details);
    }

    public List<BankDetails> getAllBankDetails() {
        return bankDetailsRepository.findAll()
            .stream()
            .filter(bd -> !Boolean.TRUE.equals(bd.getIsArchived())) // Exclude archived
            .peek(this::verifyIntegrityOrThrow) // Verify each entry
            .toList();
    }

    public Optional<BankDetails> getRandomEftBankDetails() {
        List<BankDetails> allDetails = bankDetailsRepository.findAll()
            .stream()
            .filter(bd -> !Boolean.TRUE.equals(bd.getIsArchived())) // Exclude archived
            .toList();

        if (allDetails.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(allDetails.size());
        BankDetails selected = allDetails.get(randomIndex);
        verifyIntegrityOrThrow(selected);
        return Optional.of(selected);
    }

    /**
     * Verifies the integrity of bank details by checking checksums.
     * Throws exception if tampering is detected.
     * @param bankDetails The details to verify
     * @throws SecurityException if checksums don't match (tampering detected)
     */
    private void verifyIntegrityOrThrow(BankDetails bankDetails) {
        if (!verifyChecksums(bankDetails)) {
            throw new SecurityException("CRITICAL: Bank details integrity check FAILED for ID " + bankDetails.getId() + 
                                      ". This indicates unauthorized tampering. ORDER PROCESSING BLOCKED.");
        }
    }

    public BankDetails saveOrUpdateBankDetails(BankDetails bankDetails) {
        // SECURITY: Only ADMIN users can modify bank details
        checkAdminAccess();

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

            // SECURITY: Generate checksums for tamper detection
            generateAndSetChecksums(existingDetails);
            existingDetails.setLastVerifiedAt(LocalDateTime.now());

            BankDetails saved = bankDetailsRepository.save(existingDetails);
            writeAudit(AuditAction.UPDATE, beforeUpdate, saved);
            
            System.out.println("[SECURITY] Bank details updated by admin: " + resolveActorUsername() + 
                             " at " + LocalDateTime.now());
            
            return saved;
        }

        long accountCount = bankDetailsRepository.count();
        if (accountCount >= MAX_EFT_ACCOUNTS) {
            throw new IllegalArgumentException("You can only configure up to " + MAX_EFT_ACCOUNTS + " EFT accounts.");
        }

        // SECURITY: Generate checksums for new bank details
        generateAndSetChecksums(bankDetails);
        bankDetails.setLastVerifiedAt(LocalDateTime.now());
        bankDetails.setIsArchived(false);

        BankDetails saved = bankDetailsRepository.save(bankDetails);
        writeAudit(AuditAction.CREATE, null, saved);
        
        System.out.println("[SECURITY] Bank details created by admin: " + resolveActorUsername() + 
                         " at " + LocalDateTime.now());
        
        return saved;
    }

    /**
     * Generates SHA-256 checksums for critical bank detail fields.
     * These checksums allow detection of unauthorized database tampering.
     * @param bankDetails The bank details to checksum
     */
    private void generateAndSetChecksums(BankDetails bankDetails) {
        bankDetails.setAccountNumberChecksum(encryption.generateChecksum(bankDetails.getAccountNumber()));
        bankDetails.setAccountNameChecksum(encryption.generateChecksum(bankDetails.getAccountName()));
        bankDetails.setBankNameChecksum(encryption.generateChecksum(bankDetails.getBankName()));
    }

    /**
     * Verifies checksums to detect if bank details have been tampered with.
     * @param bankDetails The bank details to verify
     * @return true if all checksums match (no tampering detected), false otherwise
     */
    public boolean verifyChecksums(BankDetails bankDetails) {
        boolean accountNumberValid = encryption.verifyChecksum(
            bankDetails.getAccountNumber(), 
            bankDetails.getAccountNumberChecksum()
        );
        boolean accountNameValid = encryption.verifyChecksum(
            bankDetails.getAccountName(), 
            bankDetails.getAccountNameChecksum()
        );
        boolean bankNameValid = encryption.verifyChecksum(
            bankDetails.getBankName(), 
            bankDetails.getBankNameChecksum()
        );

        if (!accountNumberValid || !accountNameValid || !bankNameValid) {
            System.err.println("[SECURITY ALERT] Bank details checksum verification FAILED! " +
                             "This indicates potential database tampering. " +
                             "Account#Valid=" + accountNumberValid + 
                             ", AcctName=" + accountNameValid + 
                             ", BankName=" + bankNameValid);
            return false;
        }
        
        return true;
    }

    /**
     * Checks if current user has ADMIN role.
     * Only admins can modify bank details to prevent unauthorized changes.
     * @throws org.springframework.security.access.AccessDeniedException if not admin
     */
    private void checkAdminAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SecurityException("Not authenticated. Bank details can only be modified by authenticated admins.");
        }

        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            String username = auth.getName();
            System.err.println("[SECURITY ALERT] Non-admin user '" + username + 
                             "' attempted to modify bank details at " + LocalDateTime.now());
            throw new SecurityException("Unauthorized. Only admins can modify bank details.");
        }
    }

    // Optional: Delete bank details (usually not needed for a single config entry)
    // Uses soft-delete (archival) to preserve audit trail and payment history
    public boolean deleteBankDetails(Long id) {
        // SECURITY: Only ADMIN users can delete bank details
        checkAdminAccess();

        Optional<BankDetails> existing = bankDetailsRepository.findById(id);
        if (existing.isPresent()) {
            BankDetails toArchive = existing.get();
            BankDetails beforeDelete = snapshot(toArchive);

            // Soft-delete: mark as archived instead of hard delete
            toArchive.setIsArchived(true);
            toArchive.setLastVerifiedAt(LocalDateTime.now());
            BankDetails archived = bankDetailsRepository.save(toArchive);

            writeAudit(AuditAction.DELETE, beforeDelete, archived);

            System.out.println("[SECURITY] Bank details archived (soft-deleted) by admin: " + resolveActorUsername() + 
                             " at " + LocalDateTime.now());

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
