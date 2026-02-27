package kasiKotas.service;

import kasiKotas.model.PromoCode;
import kasiKotas.repository.PromoCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PromoCodeService {

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    public PromoCode createPromoCode(PromoCode promoCode) {
        return promoCodeRepository.save(promoCode);
    }

    public List<PromoCode> getAllPromoCodes() {
        return promoCodeRepository.findAll();
    }

    public PromoCode validatePromoCode(String code, Double orderAmount) {
        // Trim whitespace from code
        String trimmedCode = code != null ? code.trim() : "";
        
        // Check if promo code exists (case-insensitive)
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code: '" + trimmedCode + "' does not exist"));

        // Check if promo code has reached usage limit
        if (promo.getUsageCount() >= promo.getMaxUsages()) {
            throw new IllegalStateException("Promo code '" + code + "' has reached its maximum usage limit of " + promo.getMaxUsages() + " times");
        }

        // Check if promo code has expired
        if (promo.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Promo code '" + code + "' has expired on " + promo.getExpiryDate());
        }

        // Check if order amount meets minimum requirement
        if (orderAmount != null && orderAmount < promo.getMinimumOrderAmount()) {
            throw new IllegalArgumentException("Order amount R" + String.format("%.2f", orderAmount) + 
                " does not meet the minimum required amount of R" + String.format("%.2f", promo.getMinimumOrderAmount()) + 
                " for promo code '" + code + "'");
        }

        return promo;
    }

    @Transactional
    public PromoCode usePromoCode(String code, Double orderAmount) {
        // Trim whitespace from code
        String trimmedCode = code != null ? code.trim() : "";
        
        // First validate the promo code
        PromoCode promo = validatePromoCode(trimmedCode, orderAmount);
        
        // Use atomic database operation to increment usage count
        int rowsUpdated = promoCodeRepository.incrementUsageCount(trimmedCode);
        
        if (rowsUpdated == 0) {
            // This means either the promo code doesn't exist or usage limit was reached
            // Let's check the current state
            PromoCode currentPromo = promoCodeRepository.findByCodeIgnoreCase(trimmedCode).orElse(null);
            if (currentPromo != null && currentPromo.getUsageCount() >= currentPromo.getMaxUsages()) {
                throw new IllegalStateException("Promo code '" + code + "' has reached its maximum usage limit of " + currentPromo.getMaxUsages() + " times");
            } else {
                throw new IllegalStateException("Failed to apply promo code '" + code + "'. Please try again.");
            }
        }
        
        // Fetch the updated promo code
        PromoCode updatedPromo = promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + trimmedCode + "' not found after update"));
        
        System.out.println("DEBUG: Promo code '" + code + "' usage count is now " + updatedPromo.getUsageCount() + " out of " + updatedPromo.getMaxUsages());
        
        return updatedPromo;
    }

    public void deletePromoCode(Long id) {
        if (!promoCodeRepository.existsById(id)) {
            throw new IllegalArgumentException("Promo code with ID " + id + " does not exist");
        }
        promoCodeRepository.deleteById(id);
    }

    /**
     * Get promo code details without validation (for admin purposes)
     */
    public PromoCode getPromoCodeByCode(String code) {
        String trimmedCode = code != null ? code.trim() : "";
        return promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + trimmedCode + "' does not exist"));
    }

    /**
     * Refresh promo code data from database (for debugging)
     */
    @Transactional
    public PromoCode refreshPromoCode(String code) {
        String trimmedCode = code != null ? code.trim() : "";
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + trimmedCode + "' does not exist"));
        
        // Force refresh from database
        promoCodeRepository.flush();
        
        return promo;
    }

    /**
     * Reset usage count for testing purposes (Admin only)
     */
    @Transactional
    public PromoCode resetUsageCount(String code) {
        String trimmedCode = code != null ? code.trim() : "";
        // Verify promo code exists first
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + trimmedCode + "' does not exist"));
        
        // Use atomic database operation to reset usage count
        int rowsUpdated = promoCodeRepository.resetUsageCount(trimmedCode);
        
        if (rowsUpdated == 0) {
            throw new IllegalStateException("Failed to reset usage count for promo code '" + code + "'");
        }
        
        // Fetch the updated promo code
        PromoCode updated = promoCodeRepository.findByCodeIgnoreCase(trimmedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + trimmedCode + "' not found after reset"));
        
        System.out.println("DEBUG: Reset usage count for promo code '" + trimmedCode + "' to " + updated.getUsageCount());
        
        return updated;
    }
}

