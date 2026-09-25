package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.model.Payment;
import kasiKotas.model.PaymentMethod;
import kasiKotas.model.PaymentStatus;
import kasiKotas.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    @Test
    void updatePaymentStatusAllowsAdminToMarkEftAsPaid() {
        Payment payment = Payment.builder()
                .id(42L)
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.EFT)
                .amount(150.0)
                .order(new Order())
                .build();

        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment updated = paymentService.updatePaymentStatus(42L, PaymentStatus.PAID);

        assertEquals(PaymentStatus.PAID, updated.getStatus());
    }

    @Test
    void updatePaymentStatusRejectsInvalidStatusTransition() {
        Payment payment = Payment.builder()
                .id(55L)
                .status(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.COD)
                .amount(60.0)
                .order(new Order())
                .build();

        when(paymentRepository.findById(55L)).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class,
                () -> paymentService.updatePaymentStatus(55L, PaymentStatus.PENDING));
    }
}
