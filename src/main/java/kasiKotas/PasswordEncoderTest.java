package kasiKotas;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Or directly in a main method for testing/generating
public class PasswordEncoderTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Blessed@94";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("Encoded Password: " + encodedPassword);
    }
}
