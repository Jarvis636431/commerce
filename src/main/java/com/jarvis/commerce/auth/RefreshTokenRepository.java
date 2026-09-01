package com.jarvis.commerce.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(String familyId);
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);
}
