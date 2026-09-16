package kasiKotas.controller;

import kasiKotas.model.Order;
import kasiKotas.model.Payment;
import kasiKotas.service.OrderService;
import kasiKotas.service.PaymentService;
import kasiKotas.service.YocoPaymentService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/yoco")
@RequiredArgsConstructor
public class YocoPaymentController {

    private final YocoPaymentService yocoPaymentService;
    private final PaymentService paymentService;
    private final OrderService orderService;

    /**
     * Creates a Yoco checkout redirect URL.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create-checkout")
    public ResponseEntity<Map<String, String>> createCheckout(@RequestBody CheckoutRequest request) {

        if (request.getOrderId() == null) {
            throw new IllegalArgumentException("orderId is required.");
        }

        if (request.getSuccessUrl() == null || request.getSuccessUrl().isBlank()
                || request.getCancelUrl() == null || request.getCancelUrl().isBlank()) {
            throw new IllegalArgumentException("successUrl and cancelUrl are required.");
        }

        String redirectUrl = yocoPaymentService.createCheckoutSession(
                request.getOrderId(),
                request.getSuccessUrl(),
                request.getCancelUrl()
        );

        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }

    /**
     * Verifies payment status after user returns from the hosted Yoco redirect.
     */
    @GetMapping("/verify/{orderId}")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @PathVariable Long orderId,
            @RequestParam(value = "checkoutId", required = false) String checkoutId) {

        Payment payment = paymentService.getPaymentByOrderId(orderId);

        if (payment != null) {
            String reference = checkoutId != null ? checkoutId : payment.getYocoCheckoutId();

            // 1. Set Payment entity status to PAID
            paymentService.markAsPaid(payment.getId(), reference);

            // 2. Set Order entity status to PROCESSING
            orderService.updateOrderStatus(orderId, Order.OrderStatus.PROCESSING);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "orderId", orderId,
                "message", "Payment verified and order set to PROCESSING."
        ));
    }

    /**
     * Webhook endpoint for Yoco payment status callbacks.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleYocoWebhook(@RequestBody Map<String, Object> payload) {

        if (payload != null && payload.containsKey("type")) {
            String eventType = (String) payload.get("type");

            if ("checkout.succeeded".equalsIgnoreCase(eventType) || "payment.succeeded".equalsIgnoreCase(eventType)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data != null) {
                    String checkoutId = (String) data.get("id");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");

                    if (metadata != null && metadata.get("paymentId") != null) {
                        Long paymentId = Long.parseLong(metadata.get("paymentId").toString());

                        // Set Payment entity status to PAID
                        paymentService.markAsPaid(paymentId, checkoutId);

                        if (metadata.get("orderId") != null) {
                            Long orderId = Long.parseLong(metadata.get("orderId").toString());


                            orderService.updateOrderStatus(orderId, Order.OrderStatus.PROCESSING);
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    @Data
    public static class CheckoutRequest {
        private Long orderId;
        private String successUrl;
        private String cancelUrl;
    }
}