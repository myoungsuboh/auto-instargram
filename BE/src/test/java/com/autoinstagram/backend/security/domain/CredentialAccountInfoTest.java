package com.autoinstagram.backend.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0024 — 자격 증명에 함께 저장하는 인스타그램 계정 정보.
 *
 * <p>이 기능의 목적은 사람이 손으로 계정 번호를 알아내 {@code .env} 에 옮겨 적던 단계를
 * 없애는 것이다. 실제로 그 단계에서 잘못된 값(숫자여야 하는 자리에 23자의 비숫자)이
 * 들어간 사례가 있었다.
 *
 * <p>여기서 지키려는 성질:
 * <ol>
 *   <li>계정 정보가 있으면 그대로 보관한다</li>
 *   <li>계정 정보가 없어도 발급 자체는 성공한다 — 조회 실패로 토큰을 잃으면 안 된다
 *       (교환은 비멱등: ADR-0009)</li>
 *   <li>빈 문자열은 "없음"과 같은 뜻으로 통일된다 — 호출부의 판정 분기를 하나로 줄인다</li>
 *   <li>{@code toString()} 에 토큰은 여전히 없다 (AGG-03 불변식 1 / POL-05)</li>
 * </ol>
 */
class CredentialAccountInfoTest {

    private static final String ENCRYPTED = "ZW5jcnlwdGVkLXRva2VuLXBsYWNlaG9sZGVy";

    private static SecurityCredential issueWith(String igUserId, String igUsername) {
        Instant issuedAt = Instant.now();
        return SecurityCredential.issue(
                ENCRYPTED, issuedAt, issuedAt.plus(60, ChronoUnit.DAYS), igUserId, igUsername);
    }

    @Test
    @DisplayName("계정 정보를 주면 그대로 보관한다")
    void keepsAccountInfo() {
        SecurityCredential credential = issueWith("17841400000000000", "my_shop");

        assertThat(credential.getIgUserId()).isEqualTo("17841400000000000");
        assertThat(credential.getIgUsername()).isEqualTo("my_shop");
    }

    @Test
    @DisplayName("계정 정보가 없어도 발급은 성공한다 — 조회 실패로 토큰을 잃으면 안 된다")
    void issuesWithoutAccountInfo() {
        SecurityCredential credential = issueWith(null, null);

        assertThat(credential.getId()).isNotNull();
        assertThat(credential.getTokenEncrypted()).isEqualTo(ENCRYPTED);
        assertThat(credential.getIgUserId()).isNull();
        assertThat(credential.getIgUsername()).isNull();
    }

    @Test
    @DisplayName("계정 정보를 뺀 기존 3인자 발급도 그대로 동작한다 (하위 호환)")
    void threeArgIssueStillWorks() {
        Instant issuedAt = Instant.now();

        SecurityCredential credential =
                SecurityCredential.issue(ENCRYPTED, issuedAt, issuedAt.plus(60, ChronoUnit.DAYS));

        assertThat(credential.getIgUserId()).isNull();
        assertThat(credential.getIgUsername()).isNull();
    }

    @Test
    @DisplayName("빈 문자열·공백은 null 로 통일한다")
    void blankBecomesNull() {
        assertThat(issueWith("", "").getIgUserId()).isNull();
        assertThat(issueWith("", "").getIgUsername()).isNull();
        assertThat(issueWith("   ", "\t").getIgUserId()).isNull();
        assertThat(issueWith("   ", "\t").getIgUsername()).isNull();
    }

    @Test
    @DisplayName("toString 에 계정 정보는 남기고 토큰은 절대 남기지 않는다")
    void toStringExposesAccountButNeverToken() {
        String text = issueWith("17841400000000000", "my_shop").toString();

        // 계정 정보는 비밀이 아니다 — 문제 추적에 필요하므로 보인다
        assertThat(text).contains("17841400000000000").contains("my_shop");
        // 토큰은 암호문조차 보이지 않아야 한다 (AGG-03 불변식 1)
        assertThat(text).doesNotContain(ENCRYPTED);
        assertThat(text).contains("token=<encrypted, not shown>");
    }
}
