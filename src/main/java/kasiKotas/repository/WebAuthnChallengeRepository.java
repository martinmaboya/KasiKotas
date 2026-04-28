package kasiKotas.repository;

import kasiKotas.model.WebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, String> {
}