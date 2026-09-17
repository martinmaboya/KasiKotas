package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.model.Payment;
import kasiKotas.model.PaymentMethod;
import kasiKotas.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class YocoPaymentService {

    private static final Logger log = LoggerFactory.getLogger(YocoPaymentService.class);

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${yoco.secret-key}")
    private String yocoSecretKey;

    @Value("${yoco.base-url}")
    private String yocoBaseUrl;

    /**
     * Initiates a hosted checkout session with Yoco for the given order ID.
     */
    @Transactional
    public String createCheckoutSession(Long orderId, String successUrl, String cancelUrl) {

        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required.");
        }

        if (successUrl == null || successUrl.isBlank()) {
            throw new IllegalArgumentException("Success URL is required.");
        }

        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalArgumentException("Cancel URL is required.");
        }

        // 1. Fetch Order from DB
        Order order = orderService.getOrderById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Order not found with ID: " + orderId)
                );

        if (order.getPaymentMethod() != PaymentMethod.YOCO) {
            throw new IllegalArgumentException("Yoco checkout is only available for Yoco orders.");
        }

        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            throw new IllegalArgumentException("Yoco checkout amount must be greater than zero.");
        }

        // 2. Fetch or create PENDING Payment entity
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() ->
                        paymentService.createPayment(
                                order,
                                PaymentMethod.YOCO,
                                order.getTotalAmount()
                        )
                );

        // 3. Convert Rands to South African Cents (R50.00 -> 5000)
        int amountInCents = BigDecimal.valueOf(order.getTotalAmount())
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        // 4. Build HTTP Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(yocoSecretKey);
        headers.set("Idempotency-Key", "order-" + order.getId());

        // 5. Build HTTP Request Body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("amount", amountInCents);
        requestBody.put("currency", "ZAR");
        requestBody.put("successUrl", successUrl);
        requestBody.put("cancelUrl", cancelUrl);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", String.valueOf(order.getId()));
        metadata.put("paymentId", String.valueOf(payment.getId()));
        requestBody.put("metadata", metadata);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String checkoutEndpoint = yocoApiBaseUrl() + "/api/checkouts";

        log.info("Sending Yoco checkout creation request for order ID: {}, amount in cents: {}", order.getId(), amountInCents);

        try {
            // 6. Execute POST Request to Yoco
            ResponseEntity<Map> response = restTemplate.exchange(
                    checkoutEndpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if ((response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED)
                    && response.getBody() != null) {

                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

                String redirectUrl = (String) responseBody.get("redirectUrl");
                String checkoutId = (String) responseBody.get("id");

                if (redirectUrl == null || redirectUrl.isBlank()) {
                    throw new IllegalStateException("Yoco response did not contain a redirectUrl.");
                }

                // 7. Store checkout ID on Payment entity
                if (checkoutId != null) {
                    payment.setYocoCheckoutId(checkoutId);
                    paymentRepository.save(payment);
                }

                log.info("Successfully created Yoco checkout session. Checkout ID: {}", checkoutId);

                return redirectUrl;
            }

            throw new IllegalStateException("Unexpected response from Yoco API: " + response.getStatusCode());

        } catch (Exception ex) {
            log.error("Failed to create Yoco checkout session for order ID: {}. Error: {}", order.getId(), ex.getMessage(), ex);
            try {
                paymentService.markAsFailedAfterCheckoutError(payment.getId());
                orderService.cancelAfterPaymentFailure(order.getId());
            } catch (Exception recoveryException) {
                log.error("Could not cancel order {} after Yoco checkout failure: {}",
                        order.getId(), recoveryException.getMessage(), recoveryException);
            }
            throw new RuntimeException("Could not initiate Yoco payment: " + ex.getMessage(), ex);
        }
    }

    private String yocoApiBaseUrl() {
        String configuredBaseUrl = yocoBaseUrl == null ? "" : yocoBaseUrl.trim();

        if (configuredBaseUrl.isBlank()
            || configuredBaseUrl.contains("online.yoco.com")
            || configuredBaseUrl.contains("api.yoco.com")) {
            log.warn("Ignoring legacy Yoco base URL configuration and using payments.yoco.com");
            return "https://payments.yoco.com";
        }

        return configuredBaseUrl.replaceAll("/+$", "");
    }
}