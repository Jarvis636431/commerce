package com.jarvis.commerce.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${commerce.security.permit-all:false}") boolean permitAll,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (permitAll) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                    .requestMatchers("/api/users/**", "/api/orders/**", "/api/payments/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                    .anyRequest().authenticated());
        }
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean
    SecretKey jwtSecretKey(@Value("${commerce.security.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey,
                          @Value("${commerce.security.issuer:commerce}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
