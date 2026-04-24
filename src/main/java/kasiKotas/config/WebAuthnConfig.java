package kasiKotas.config;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import kasiKotas.service.passkey.WebAuthnCredentialRepositoryAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebAuthnConfig {

    @Value("${webauthn.rp-id}")
    private String rpId;

    @Value("${webauthn.rp-name}")
    private String rpName;

    @Value("${webauthn.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public RelyingParty relyingParty(WebAuthnCredentialRepositoryAdapter credentialRepository) {
        Set<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        validateOrigins(origins);

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId)
                        .name(rpName)
                        .build())
                .credentialRepository(credentialRepository)
            .origins(origins)
                .build();
    }

    private void validateOrigins(Set<String> origins) {
        for (String origin : origins) {
            if (origin.startsWith("https://")) {
                continue;
            }
            if (origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1")) {
                continue;
            }
            throw new IllegalStateException("Insecure WebAuthn origin is not allowed: " + origin);
        }
    }
}
