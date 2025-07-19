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

    public PromoCode validatePromoCode(String code) {
        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Promo code not found"));

        if (promo.getUsageCount() >= promo.getMaxUsages()) {
            throw new RuntimeException("Promo code usage limit reached");
        }

        if (promo.getExpiryDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Promo code has expired");
        }

        return promo;
    }

    public void usePromoCode(String code) {
        PromoCode promo = validatePromoCode(code);
        promo.setUsageCount(promo.getUsageCount() + 1);
        promoCodeRepository.save(promo);
    }

    public void deletePromoCode(Long id) {
        promoCodeRepository.deleteById(id);
    }
}

