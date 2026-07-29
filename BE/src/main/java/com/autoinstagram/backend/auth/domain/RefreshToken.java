package com.autoinstagram.backend.auth.domain;

import com.autoinstagram.backend.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 갱신(refresh) 토큰. SKL-AUTHN-AUTHZ 규칙 2 구현의 저장 측.
 *
 * <p>토큰 원문은 저장하지 않고 SHA-256 해시만 갖는다 —
 * DB 가 유출돼도 그 값만으로는 로그인할 수 없게 한다.
 * 원문은 httpOnly 쿠키(브라우저)에만 존재한다.
 *
 * <p>사용 시 회전(rotation)한다: 갱신 요청이 오면 기존 토큰을 폐기하고 새 토큰을 발급한다.
 * 폐기된 토큰이 재사용되면 탈취 신호로 간주할 수 있다.
 */
@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "app_account_id", nullable = false, updatable = false)
    private UUID appAccountId;

    /** 토큰 원문의 SHA-256 (64자 hex). 원문은 여기 없다. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** NULL = 유효. 회전·로그아웃 시 채워진다. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
        // JPA 전용
    }

    private RefreshToken(UUID id, UUID appAccountId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.appAccountId = appAccountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(UUID appAccountId, String tokenHash, Instant expiresAt) {
        if (tokenHash == null || tokenHash.length() != 64) {
            // DB CHECK 제약과 같은 규칙을 애플리케이션에서도 막는다 (fail-fast)
            throw new IllegalArgumentException("tokenHash 는 64자 SHA-256 hex 여야 합니다");
        }
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt 은 현재보다 미래여야 합니다");
        }
        return new RefreshToken(UUID.randomUUID(), appAccountId, tokenHash, expiresAt);
    }

    /** 아직 쓸 수 있는 토큰인지 — 폐기되지 않았고, 만료되지 않았고, 논리 삭제되지 않았다. */
    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now()) && isActive();
    }

    /** 폐기한다. 이미 폐기된 토큰은 최초 폐기 시각을 보존한다. */
    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAppAccountId() {
        return appAccountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    /** 해시조차 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "RefreshToken{id=" + id + ", appAccountId=" + appAccountId
                + ", expiresAt=" + expiresAt + ", revoked=" + (revokedAt != null) + "}";
    }
}
