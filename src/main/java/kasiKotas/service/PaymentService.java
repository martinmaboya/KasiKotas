package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.model.Payment;
import kasiKotas.model.PaymentMethod;
import kasiKotas.model.PaymentStatus;
import kasiKotas.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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
    public Payment updatePaymentStatus(Long paymentId, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        return applyStatusTransition(payment, newStatus, null);
    }

    @Transactional
    public Payment updatePaymentStatusByOrderId(Long orderId, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        return applyStatusTransition(payment, newStatus, null);
    }

    @Transactional
    public Payment updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String providerReference) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        return applyStatusTransition(payment, newStatus, providerReference);
    }

    @Transactional
    public Payment updatePaymentStatusByYocoCheckoutId(String yocoCheckoutId, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findByYocoCheckoutId(yocoCheckoutId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for Yoco Checkout ID: " + yocoCheckoutId));
        return applyStatusTransition(payment, newStatus, yocoCheckoutId);
    }

    @Transactional
    public Payment markAsPaid(Long paymentId, String providerReference) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        return applyStatusTransition(payment, PaymentStatus.PAID, providerReference);
    }

    @Transactional
    public Payment markAsFailed(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        return applyStatusTransition(payment, PaymentStatus.FAILED, null);
    }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Payment markAsFailedAfterCheckoutError(Long paymentId) {
                return markAsFailed(paymentId);
        }

    @Transactional
    public Payment cancelPayment(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );

        return applyStatusTransition(payment, PaymentStatus.CANCELLED, null);
    }

    private Payment applyStatusTransition(Payment payment, PaymentStatus newStatus, String providerReference) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment is required.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("Payment status is required.");
        }

        PaymentStatus currentStatus = payment.getStatus() == null ? PaymentStatus.PENDING : payment.getStatus();
        if (currentStatus == newStatus) {
            return payment;
        }

        switch (newStatus) {
            case PENDING -> {
                if (currentStatus != PaymentStatus.PENDING) {
                    throw new IllegalStateException("Payment can only return to pending from a pending state.");
                }
            }
            case PAID -> {
                if (currentStatus == PaymentStatus.CANCELLED || currentStatus == PaymentStatus.REFUNDED) {
                    throw new IllegalStateException("A cancelled or refunded payment cannot be marked as paid.");
                }
                payment.setPaidAt(LocalDateTime.now());
                if (providerReference != null && !providerReference.isBlank()) {
                    payment.setProviderReference(providerReference);
                }
            }
            case FAILED -> {
                if (currentStatus == PaymentStatus.PAID || currentStatus == PaymentStatus.REFUNDED) {
                    throw new IllegalStateException("A paid or refunded payment cannot be marked as failed.");
                }
                payment.setPaidAt(null);
            }
            case CANCELLED -> {
                if (currentStatus == PaymentStatus.PAID || currentStatus == PaymentStatus.REFUNDED) {
                    throw new IllegalStateException("A paid or refunded payment cannot be cancelled.");
                }
                payment.setPaidAt(null);
            }
            case REFUNDED -> {
                if (currentStatus != PaymentStatus.PAID) {
                    throw new IllegalStateException("Only a paid payment can be refunded.");
                }
            }
            default -> throw new IllegalStateException("Unsupported payment status: " + newStatus);
        }

        payment.setStatus(newStatus);
        return paymentRepository.save(payment);
    }
}