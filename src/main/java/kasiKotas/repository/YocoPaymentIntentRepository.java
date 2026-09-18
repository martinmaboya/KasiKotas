package kasiKotas.repository;

import kasiKotas.model.YocoPaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YocoPaymentIntentRepository extends JpaRepository<YocoPaymentIntent, Long> {

    Optional<YocoPaymentIntent> findByYocoCheckoutId(String yocoCheckoutId);
}
