package com.jarvis.commerce.auth;

import com.jarvis.commerce.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, String familyId, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(OffsetDateTime now) { return !expiresAt.isAfter(now); }
    public boolean isUsed() { return usedAt != null; }
    public boolean isRevoked() { return revokedAt != null; }
    public void markUsed(OffsetDateTime now, Long replacementId) {
        usedAt = now;
        replacedByTokenId = replacementId;
    }
    public void revoke(OffsetDateTime now) { if (revokedAt == null) revokedAt = now; }

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFamilyId() { return familyId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
