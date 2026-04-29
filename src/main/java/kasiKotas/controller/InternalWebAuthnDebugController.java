package kasiKotas.controller;

import kasiKotas.service.passkey.PasskeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/webauthn")
public class InternalWebAuthnDebugController {

    private final PasskeyService passkeyService;

    public InternalWebAuthnDebugController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @GetMapping("/login/options")
    public ResponseEntity<Map<String, Object>> loginOptions(@RequestParam String email) {
        Map<String, Object> opts = passkeyService.createLoginOptions(email);
        return ResponseEntity.ok(opts);
    }

    @GetMapping("/register/options")
    public ResponseEntity<Map<String, Object>> registerOptions(@RequestParam String email) {
        Map<String, Object> opts = passkeyService.createRegistrationOptions(email);
        return ResponseEntity.ok(opts);
    }
}
