package kasiKotas.repository;

import kasiKotas.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByYocoCheckoutId(String yocoCheckoutId);

    Optional<Payment> findByProviderReference(String providerReference);
}