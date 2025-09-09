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
        // Check if promo code exists
        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code: '" + code + "' does not exist"));

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
        // Validate the promo code first
        PromoCode promo = validatePromoCode(code, orderAmount);
        
        // Increment usage count
        promo.setUsageCount(promo.getUsageCount() + 1);
        
        // Save the updated promo code and flush to ensure immediate persistence
        PromoCode updatedPromo = promoCodeRepository.saveAndFlush(promo);
        
        System.out.println("DEBUG: Promo code '" + code + "' usage updated from " + 
                          (promo.getUsageCount() - 1) + " to " + updatedPromo.getUsageCount());
        
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
        return promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Promo code '" + code + "' does not exist"));
    }
}

