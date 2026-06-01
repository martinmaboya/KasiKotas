package kasiKotas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    @Value("${app.base-url:}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Ping every 10 minutes to prevent Render free tier from sleeping
    @Scheduled(fixedDelay = 600000)
    public void keepAlive() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        try {
            restTemplate.getForObject(baseUrl + "/", String.class);
            log.debug("Keep-alive ping sent to {}", baseUrl);
        } catch (Exception e) {
            log.debug("Keep-alive ping failed: {}", e.getMessage());
        }
    }
}
