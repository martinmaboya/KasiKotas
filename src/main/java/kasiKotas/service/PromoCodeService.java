package kasiKotas.service;

import kasiKotas.model.PromoCode;
import kasiKotas.repository.PromoCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Promo code not found"));

        if (promo.getUsageCount() >= promo.getMaxUsages()) {
            throw new RuntimeException("Promo code usage limit reached");
        }

        if (promo.getExpiryDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Promo code has expired");
        }

        if (orderAmount != null && orderAmount < promo.getMinimumOrderAmount()) {
            throw new RuntimeException("Order amount does not meet minimum required for promo code");
        }

        return promo;
    }

    public void usePromoCode(String code, Double orderAmount) {
        PromoCode promo = validatePromoCode(code, orderAmount);
        promo.setUsageCount(promo.getUsageCount() + 1);
        if (promo.getUsageCount() >= promo.getMaxUsages()) {
            promoCodeRepository.delete(promo);
        } else {
            promoCodeRepository.save(promo);
        }
    }

    public void deletePromoCode(Long id) {
        promoCodeRepository.deleteById(id);
    }
}

