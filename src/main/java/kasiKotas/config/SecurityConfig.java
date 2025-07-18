package kasiKotas.config;

            import kasiKotas.security.JwtAuthFilter;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
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
                                        // ... keep your existing permitAll paths ...
                                        "/",
                                        "/home",
                                        "/auth/**",
                                        "/public/**",
                                        "/register",
                                        "/api/users/register",
                                        "/api/products",
                                        "/product-images/**",
                                        "/api/extras",
                                        "/api/sauces",
                                        "/api/promo-codes",
                                        "/api/promo-codes/validate/**",
                                        "/api/promo-codes/use/**",
                                        "/api/promo-codes/**",// This will cover /api/promo-codes/create and any other /api/promo-codes paths
                                        "/api/auth/**" // This will cover /api/auth/login and any other /api/auth paths
                                ).permitAll()
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