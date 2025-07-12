package kasiKotas;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// In your Spring configuration or service where you need to encode
// You typically inject it as a bean
public class MyService {

    private final PasswordEncoder passwordEncoder;

    public MyService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(String plainPassword) {
        String encodedPassword = passwordEncoder.encode(plainPassword);
        // Save encodedPassword to your database
        System.out.println("Encoded Password: " + encodedPassword);
    }
}

