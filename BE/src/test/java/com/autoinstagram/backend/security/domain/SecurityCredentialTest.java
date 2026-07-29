package com.autoinstagram.backend.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AGG-03 SecurityCredential 의 도메인 불변식 검증 (2_ddd.md §2 CTX-02).
 *
 * <p>불변식 2개:
 * <ol>
 *   <li>token string must be masked in logs</li>
 *   <li>expiresAt &gt; issuedAt</li>
 * </ol>
 */
class SecurityCredentialTest {

    private static final String ENCRYPTED = "ZW5jcnlwdGVkLXRva2VuLXBsYWNlaG9sZGVy";

    @Test
    @DisplayName("불변식 2: 만료가 발급보다 뒤면 생성된다")
    void issuesWhenExpiryAfterIssuedAt() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(60, ChronoUnit.DAYS);

        SecurityCredential credential = SecurityCredential.issue(ENCRYPTED, issuedAt, expiresAt);

        assertThat(credential.getId()).isNotNull();
        assertThat(credential.getTokenEncrypted()).isEqualTo(ENCRYPTED);
        assertThat(credential.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(credential.isExpired()).isFalse();
    }

    @Test
    @DisplayName("불변식 2: 만료가 발급보다 이전이면 거부한다")
    void rejectsExpiryBeforeIssuedAt() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> SecurityCredential.issue(ENCRYPTED, issuedAt, expiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt > issuedAt");
    }

    @Test
    @DisplayName("불변식 2: 만료와 발급이 같아도 거부한다 (초과여야 함)")
    void rejectsExpiryEqualToIssuedAt() {
        Instant sameMoment = Instant.now();

        assertThatThrownBy(() -> SecurityCredential.issue(ENCRYPTED, sameMoment, sameMoment))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("불변식 1: toString 에 토큰이 (암호문조차) 들어가지 않는다")
    void toStringNeverContainsToken() {
        SecurityCredential credential = SecurityCredential.issue(
                ENCRYPTED, Instant.now(), Instant.now().plus(60, ChronoUnit.DAYS));

        String printed = credential.toString();

        // 엔티티를 로그에 그대로 찍는 것이 토큰 유출의 대표 경로다
        assertThat(printed).doesNotContain(ENCRYPTED);
        assertThat(printed).contains("<encrypted, not shown>");
    }

    @Test
    @DisplayName("암호화된 토큰이 비어 있으면 거부한다")
    void rejectsBlankToken() {
        Instant now = Instant.now();
        Instant later = now.plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> SecurityCredential.issue("  ", now, later))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecurityCredential.issue(null, now, later))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만료된 자격 증명은 isExpired 가 true, 남은 시간은 0")
    void detectsExpiredCredential() {
        Instant issuedAt = Instant.now().minus(70, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().minus(10, ChronoUnit.DAYS);

        SecurityCredential credential = SecurityCredential.issue(ENCRYPTED, issuedAt, expiresAt);

        assertThat(credential.isExpired()).isTrue();
        // 음수가 새어 나가면 API 응답의 expiresIn 제약(>0)을 깬다
        assertThat(credential.remainingSeconds()).isZero();
    }
}
