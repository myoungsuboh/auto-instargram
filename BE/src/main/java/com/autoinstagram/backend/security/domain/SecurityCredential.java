package com.autoinstagram.backend.security.domain;

import com.autoinstagram.backend.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * ENT-03 / AGG-03 — 인스타그램 API 액세스 토큰 및 시크릿 관리 애그리거트 루트.
 *
 * <p>2_ddd.md AGG-03 도메인 규칙(불변식) 2개를 이 클래스가 책임진다:
 * <ol>
 *   <li>{@code token string must be masked in logs}
 *       → {@link #toString()} 에 토큰을 넣지 않고, 접근자도 암호문만 노출한다</li>
 *   <li>{@code expiresAt > issuedAt}
 *       → {@link #issue} 에서 검증하고 DB CHECK 제약({@code ck_security_credentials_expiry})으로 이중 강제</li>
 * </ol>
 *
 * <p>POL-05(토큰 전문 노출률 0%): 이 엔티티는 <b>암호문만</b> 보관한다.
 * 평문 토큰은 {@link com.autoinstagram.backend.security.service.TokenCipher} 를 거쳐야만 얻을 수 있고,
 * 그 결과를 필드에 저장하지 않는다.
 */
@Entity
@Table(name = "security_credentials")
public class SecurityCredential extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 암호화된 액세스 토큰 (1_spack.md ENT-03.token).
     * 컬럼명에 {@code encrypted} 를 명시해, 평문을 넣는 실수를 코드 리뷰에서 잡을 수 있게 했다.
     */
    @Column(name = "token_encrypted", nullable = false)
    private String tokenEncrypted;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SecurityCredential() {
        // JPA 전용
    }

    private SecurityCredential(UUID id, String tokenEncrypted, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.tokenEncrypted = tokenEncrypted;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 새 자격 증명을 발급한다.
     *
     * @param tokenEncrypted 이미 암호화된 토큰. 평문을 넘기면 안 된다
     * @throws IllegalArgumentException AGG-03 불변식 위반 시
     */
    public static SecurityCredential issue(String tokenEncrypted, Instant issuedAt, Instant expiresAt) {
        if (tokenEncrypted == null || tokenEncrypted.isBlank()) {
            throw new IllegalArgumentException("암호화된 토큰은 필수입니다");
        }
        if (issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("issuedAt 과 expiresAt 은 필수입니다");
        }
        // AGG-03 불변식 2
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "만료 시각은 발급 시각보다 뒤여야 합니다 (AGG-03 불변식: expiresAt > issuedAt)");
        }
        return new SecurityCredential(UUID.randomUUID(), tokenEncrypted, issuedAt, expiresAt);
    }

    /** 이미 만료됐는지. */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /** 남은 수명(초). 만료됐으면 0. API-05 응답의 {@code expiresIn} 계산에 쓴다. */
    public long remainingSeconds() {
        long seconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(seconds, 0L);
    }

    public UUID getId() {
        return id;
    }

    /** 암호문. 복호화는 TokenCipher 를 통해서만 한다. */
    public String getTokenEncrypted() {
        return tokenEncrypted;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * AGG-03 불변식 1 구현: 토큰(암호문조차) 을 포함하지 않는다.
     *
     * <p>엔티티를 로그에 그대로 찍는 것은 토큰이 유출되는 가장 흔한 경로다.
     * 기본 toString 을 쓰면 필드가 전부 출력되므로 반드시 덮어써야 한다.
     */
    @Override
    public String toString() {
        return "SecurityCredential{id=" + id
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt
                + ", token=<encrypted, not shown>}";
    }
}
