package com.jarvis.commerce.auth;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.user.User;
import com.jarvis.commerce.user.UserRepository;
import com.jarvis.commerce.user.UserRole;
import com.jarvis.commerce.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock,
                       @Value("${commerce.security.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = normalizePhone(request.phone());
        ensureUnique(username, email, phone);
        User user = userRepository.save(new User(username, email, phone,
                passwordEncoder.encode(request.password()), UserRole.USER));
        return createTokenPair(user, UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String account = request.account().trim().toLowerCase(Locale.ROOT);
        User user = (account.contains("@")
                ? userRepository.findByEmailIgnoreCase(account)
                : userRepository.findByUsernameIgnoreCase(account)).orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return createTokenPair(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(RefreshRequest request) {
        OffsetDateTime now = now();
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (current.isUsed()) {
            revokeFamily(current.getFamilyId(), now);
            refreshTokenRepository.flush();
            throw new UnauthorizedException("Refresh token reuse detected; sign in again");
        }
        if (current.isRevoked() || current.isExpiredAt(now)) {
            current.revoke(now);
            refreshTokenRepository.flush();
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        User user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            revokeFamily(current.getFamilyId(), now);
            throw new UnauthorizedException("User is disabled");
        }

        String rawToken = generateRefreshToken();
        RefreshToken replacement = refreshTokenRepository.saveAndFlush(new RefreshToken(
                user, hash(rawToken), current.getFamilyId(), now.plus(refreshTokenTtl)));
        current.markUsed(now, replacement.getId());
        refreshTokenRepository.flush();
        return response(user, rawToken);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .ifPresent(token -> token.revoke(now()));
    }

    @Transactional
    public void logoutAll(long userId) {
        OffsetDateTime now = now();
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> token.revoke(now));
    }

    private TokenResponse createTokenPair(User user, String familyId) {
        String rawToken = generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), familyId,
                now().plus(refreshTokenTtl)));
        return response(user, rawToken);
    }

    private TokenResponse response(User user, String refreshToken) {
        return new TokenResponse(jwtService.createAccessToken(user), refreshToken,
                "Bearer", jwtService.accessTokenExpiresInSeconds());
    }

    private void revokeFamily(String familyId, OffsetDateTime now) {
        refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId)
                .forEach(token -> token.revoke(now));
    }

    private void ensureUnique(String username, String email, String phone) {
        if (userRepository.existsByUsernameIgnoreCase(username)) throw new ConflictException("Username is already in use");
        if (userRepository.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already in use");
        if (phone != null && userRepository.existsByPhone(phone)) throw new ConflictException("Phone is already in use");
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC); }
    private String normalizePhone(String phone) { return phone == null || phone.isBlank() ? null : phone.trim(); }
    private UnauthorizedException invalidCredentials() { return new UnauthorizedException("Invalid account or password"); }
}
