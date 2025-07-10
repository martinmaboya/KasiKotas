package kasiKotas.security;

        import io.jsonwebtoken.Jwts;
        import io.jsonwebtoken.SignatureAlgorithm;
        import io.jsonwebtoken.security.Keys;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.stereotype.Component;

        import javax.crypto.SecretKey;
        import java.util.Date;
        import java.util.Base64;

        @Component
        public class JwtUtil {

            private final SecretKey secretKey;
            private final long jwtExpirationMs = 86400000; // 1 day

            public JwtUtil(@Value("${jwt.secret}") String secret) {
                this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
            }

            public String generateToken(String username) {
                return Jwts.builder()
                        .setSubject(username)
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                        .signWith(secretKey, SignatureAlgorithm.HS512)
                        .compact();
            }

            public String getUsernameFromToken(String token) {
                return Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody()
                        .getSubject();
            }

            public boolean validateToken(String token) {
                try {
                    Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }