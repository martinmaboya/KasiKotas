package kasiKotas.config;


import kasiKotas.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // ✅ Add this import
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/",
                                "/home",
                                "/auth/**",
                                "/public/**",
                                "/register",
                                "/api/users/register",
                                "/api/products/get-all",           // ✅ Public: view products
                                "/api/products/*/image",           // Public: get product image
                                "/product-images/**",
                                "/api/extras",
                                "/api/sauces",
                                "/api/promo-codes/validate/**",    // Public: validate promo codes
                                "/api/promo-codes/use/**",         // Public: use promo codes
                                "/api/auth/forgot-password",       // Public: forgot password
                                "/api/auth/reset-password",        // Public: reset password
                                "/api/auth/login",                 // Public: login
                                "/api/auth/get-reset-token"        // Public: get reset token
                        ).permitAll()
                        // Admin-only promo code rules AFTER permitAll
                        .requestMatchers(HttpMethod.GET, "/api/promo-codes").hasRole("ADMIN")         // Admin: GET all promo codes
                        .requestMatchers(HttpMethod.POST, "/api/promo-codes").hasRole("ADMIN")        // Admin: CREATE promo codes
                        .requestMatchers(HttpMethod.DELETE, "/api/promo-codes/**").hasRole("ADMIN")   // Admin: DELETE promo codes
                        .requestMatchers("/api/products/**").hasRole("ADMIN")                         // Admin: product management
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}