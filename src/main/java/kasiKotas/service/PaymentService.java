package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.model.Payment;
import kasiKotas.model.PaymentMethod;
import kasiKotas.model.PaymentStatus;
import kasiKotas.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(
            Order order,
            PaymentMethod paymentMethod,
            Double amount
    ) {

        if (order == null) {
            throw new IllegalArgumentException("Order is required.");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required.");
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        // Prevent duplicate payments for the same order
        Payment existingPayment =
                paymentRepository.findByOrderId(order.getId())
                        .orElse(null);

        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = Payment.builder()
                .order(order)
                .status(PaymentStatus.PENDING)
                .paymentMethod(paymentMethod)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(Long orderId) {

        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for order: " + orderId
                        )
                );
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByYocoCheckoutId(String yocoCheckoutId) {

        return paymentRepository.findByYocoCheckoutId(yocoCheckoutId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for Yoco Checkout ID: " + yocoCheckoutId
                        )
                );
    }

    @Transactional
    public Payment markAsPaid(Long paymentId, String providerReference) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        payment.setStatus(PaymentStatus.PAID);
        payment.setProviderReference(providerReference);
        payment.setPaidAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markAsFailed(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        payment.setStatus(PaymentStatus.FAILED);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment cancelPayment(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        payment.setStatus(PaymentStatus.CANCELLED);

        return paymentRepository.save(payment);
    }
}