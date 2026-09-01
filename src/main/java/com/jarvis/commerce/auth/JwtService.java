package com.jarvis.commerce.auth;

import com.jarvis.commerce.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(JwtEncoder jwtEncoder, Clock clock,
                      @Value("${commerce.security.access-token-ttl:PT15M}") Duration accessTokenTtl,
                      @Value("${commerce.security.issuer:commerce}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String createAccessToken(User user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenTtl))
                .id(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenExpiresInSeconds() { return accessTokenTtl.toSeconds(); }
}
