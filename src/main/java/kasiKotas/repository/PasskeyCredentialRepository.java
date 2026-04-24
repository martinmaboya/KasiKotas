package kasiKotas.repository;

import kasiKotas.model.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findByUserId(Long userId);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    long countByUserId(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
