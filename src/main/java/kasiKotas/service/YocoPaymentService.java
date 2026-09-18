package kasiKotas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kasiKotas.model.*;
import kasiKotas.repository.YocoPaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class YocoPaymentService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final YocoPaymentIntentRepository intentRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${yoco.secret-key}")
    private String yocoSecretKey;

    @Value("${yoco.base-url}")
    private String yocoBaseUrl;

    @Transactional
    public String createCheckoutSession(Map<String, Object> orderRequest, User user,
                                        String successUrl, String cancelUrl) {
        if (orderRequest == null || orderRequest.isEmpty()) {
            throw new IllegalArgumentException("order is required.");
        }
        if (successUrl == null || successUrl.isBlank() || cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalArgumentException("successUrl and cancelUrl are required.");
        }

        Order order = toOrder(orderRequest, user);
        double amount = quoteOrder(order);
        YocoPaymentIntent intent = intentRepository.save(YocoPaymentIntent.builder()
                .user(user)
                .orderPayload(serialize(orderRequest))
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        int amountInCents = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(yocoSecretKey);
        headers.set("Idempotency-Key", "intent-" + intent.getId());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("amount", amountInCents);
        requestBody.put("currency", "ZAR");
        requestBody.put("successUrl", UriComponentsBuilder.fromUriString(successUrl)
            .queryParam("intentId", intent.getId())
            .build()
            .toUriString());
        requestBody.put("cancelUrl", cancelUrl);
        requestBody.put("metadata", Map.of("intentId", String.valueOf(intent.getId())));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    yocoApiBaseUrl() + "/api/checkouts", HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class);
            if ((response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED)
                    || response.getBody() == null) {
                throw new IllegalStateException("Unexpected response from Yoco API: " + response.getStatusCode());
            }
            Map<String, Object> responseBody = response.getBody();
            String redirectUrl = (String) responseBody.get("redirectUrl");
            String checkoutId = (String) responseBody.get("id");
            if (redirectUrl == null || redirectUrl.isBlank() || checkoutId == null || checkoutId.isBlank()) {
                throw new IllegalStateException("Yoco response did not contain checkout details.");
            }
            intent.setYocoCheckoutId(checkoutId);
            intentRepository.save(intent);
            return redirectUrl;
        } catch (Exception ex) {
            intent.setStatus(PaymentStatus.FAILED);
            intentRepository.save(intent);
            throw new RuntimeException("Could not initiate Yoco payment: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void confirmIntent(Long intentId, String checkoutId) {
        YocoPaymentIntent intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Yoco payment intent not found: " + intentId));
        if (intent.getStatus() == PaymentStatus.PAID) {
            return;
        }
        Order order = toOrder(deserialize(intent.getOrderPayload()), intent.getUser());
        Order savedOrder = orderService.createOrder(order, PaymentMethod.YOCO);
        Payment payment = paymentService.createPayment(savedOrder, PaymentMethod.YOCO, savedOrder.getTotalAmount());
        paymentService.markAsPaid(payment.getId(), checkoutId);
        orderService.updateOrderStatus(savedOrder.getId(), Order.OrderStatus.PROCESSING);
        intent.setOrderId(savedOrder.getId());
        intent.setStatus(PaymentStatus.PAID);
        intentRepository.save(intent);
    }

    @Transactional
    public void cancelIntent(Long intentId) {
        intentRepository.findById(intentId).ifPresent(intent -> {
            if (intent.getStatus() == PaymentStatus.PENDING) {
                intent.setStatus(PaymentStatus.CANCELLED);
                intentRepository.save(intent);
            }
        });
    }

    private double quoteOrder(Order order) {
        try {
            orderService.quoteOrder(order, PaymentMethod.YOCO);
            throw new IllegalStateException("Order quote did not roll back.");
        } catch (OrderService.OrderQuoteException quote) {
            return quote.getTotal();
        }
    }

    private Order toOrder(Map<String, Object> payload, User user) {
        Order order = objectMapper.convertValue(payload, Order.class);
        order.setUser(new User(user.getId()));
        order.setPaymentMethod(PaymentMethod.YOCO);
        return order;
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid order payload.", ex);
        }
    }

    private Map<String, Object> deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored Yoco order payload is invalid.", ex);
        }
    }

    private String yocoApiBaseUrl() {
        String configuredBaseUrl = yocoBaseUrl == null ? "" : yocoBaseUrl.trim();
        if (configuredBaseUrl.isBlank() || configuredBaseUrl.contains("online.yoco.com")
                || configuredBaseUrl.contains("api.yoco.com")) {
            return "https://payments.yoco.com";
        }
        return configuredBaseUrl.replaceAll("/+$", "");
    }
}
