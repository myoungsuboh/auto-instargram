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

    /**
     * 토큰 교환 시 {@code GET /me} 로 함께 받아온 인스타그램 계정 번호 (ADR-0024).
     *
     * <p>⚠️ 1_spack.md ENT-03 이 규정한 속성 3개보다 많다 — 의도된 명세 이탈이다.
     * 사람이 손으로 알아내 환경변수에 적던 값을 서버가 자동으로 갖게 하는 것이 목적이다.
     *
     * <p>NULL 일 수 있다: 이 기능 이전에 발급된 행이거나, {@code /me} 조회가 실패한 경우다.
     * 조회 실패로 토큰 교환을 되돌릴 수는 없다(교환은 비멱등 — ADR-0009).
     * 그 경우 게시 시 {@code INSTAGRAM_USER_ID} 환경변수로 대체한다.
     */
    @Column(name = "ig_user_id", length = 64)
    private String igUserId;

    /**
     * 계정 이름. 화면에 "어느 계정에 연결됐는지" 보여주기 위한 값이다.
     *
     * <p>식별자로 쓰지 않는다 — 사용자가 언제든 바꿀 수 있다.
     */
    @Column(name = "ig_username", length = 64)
    private String igUsername;

    protected SecurityCredential() {
        // JPA 전용
    }

    private SecurityCredential(UUID id, String tokenEncrypted, Instant issuedAt, Instant expiresAt,
                               String igUserId, String igUsername) {
        this.id = id;
        this.tokenEncrypted = tokenEncrypted;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.igUserId = igUserId;
        this.igUsername = igUsername;
    }

    /**
     * 계정 정보 없이 발급한다 — 계정 조회가 실패했거나 필요 없는 경우.
     *
     * @param tokenEncrypted 이미 암호화된 토큰. 평문을 넘기면 안 된다
     * @throws IllegalArgumentException AGG-03 불변식 위반 시
     */
    public static SecurityCredential issue(String tokenEncrypted, Instant issuedAt, Instant expiresAt) {
        return issue(tokenEncrypted, issuedAt, expiresAt, null, null);
    }

    /**
     * 계정 정보까지 함께 발급한다 (ADR-0024).
     *
     * <p>계정 정보를 나중에 UPDATE 하지 않고 처음 저장에 포함시키는 이유:
     * 쓰기가 한 번으로 끝나고, "토큰은 저장됐는데 계정 정보만 빠진" 중간 상태가 생기지 않는다.
     *
     * @param igUserId   계정 번호. 알 수 없으면 {@code null}
     * @param igUsername 계정 이름. 알 수 없으면 {@code null}
     * @throws IllegalArgumentException AGG-03 불변식 위반 시
     */
    public static SecurityCredential issue(String tokenEncrypted, Instant issuedAt, Instant expiresAt,
                                           String igUserId, String igUsername) {
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
        return new SecurityCredential(UUID.randomUUID(), tokenEncrypted, issuedAt, expiresAt,
                blankToNull(igUserId), blankToNull(igUsername));
    }

    /** 빈 문자열은 "값이 없음"과 같은 뜻이므로 NULL 로 통일한다 — 이후 판정 분기를 하나로 줄인다. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
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

    /** 계정 번호. 알 수 없으면 {@code null} — 호출자가 환경변수로 대체해야 한다. */
    public String getIgUserId() {
        return igUserId;
    }

    /** 계정 이름. 알 수 없으면 {@code null}. */
    public String getIgUsername() {
        return igUsername;
    }

    /**
     * AGG-03 불변식 1 구현: 토큰(암호문조차) 을 포함하지 않는다.
     *
     * <p>엔티티를 로그에 그대로 찍는 것은 토큰이 유출되는 가장 흔한 경로다.
     * 기본 toString 을 쓰면 필드가 전부 출력되므로 반드시 덮어써야 한다.
     */
    @Override
    public String toString() {
        // igUserId·igUsername 은 비밀이 아니라 계정 식별 정보이므로 남겨도 된다.
        // 토큰만 절대 넣지 않는다.
        return "SecurityCredential{id=" + id
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt
                + ", igUserId=" + igUserId
                + ", igUsername=" + igUsername
                + ", token=<encrypted, not shown>}";
    }
}
