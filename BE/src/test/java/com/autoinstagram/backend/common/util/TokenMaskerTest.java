package com.autoinstagram.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * POL-05 검증: "모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함"
 *
 * <p>이 테스트가 지키는 것은 "마스킹 함수가 동작한다" 가 아니라
 * "마스킹을 거친 문자열에 원문이 남아 있지 않다" 다 — 후자가 정책의 실제 요구사항이다.
 */
class TokenMaskerTest {

    private static final String META_TOKEN =
            "EAAGm0PX4ZCpsBAJ7ZC8ZBxZDZD1234567890abcdefghijklmnopqrstuvwxyz";

    @Test
    @DisplayName("mask: 토큰 원문이 결과에 남지 않는다")
    void maskDoesNotLeakOriginal() {
        String masked = TokenMasker.mask(META_TOKEN);

        assertThat(masked).doesNotContain(META_TOKEN);
        // 앞 4자만 노출되고 나머지는 사라져야 한다
        assertThat(masked).startsWith("EAAG");
        assertThat(masked).contains("***");
        // 원문의 뒷부분이 조금이라도 남아 있으면 안 된다
        assertThat(masked).doesNotContain(META_TOKEN.substring(10));
    }

    @Test
    @DisplayName("mask: 길이 정보는 남긴다 (비었는지/잘렸는지 조사 가능해야 함)")
    void maskKeepsLength() {
        assertThat(TokenMasker.mask(META_TOKEN)).contains("len=" + META_TOKEN.length());
    }

    @Test
    @DisplayName("mask: 짧은 값은 앞자리도 노출하지 않는다")
    void maskHidesShortValuesEntirely() {
        String masked = TokenMasker.mask("short123");

        assertThat(masked).doesNotContain("short");
        assertThat(masked).startsWith("***");
    }

    @Test
    @DisplayName("mask: null·빈 값에도 예외 없이 안전한 표기를 돌려준다")
    void maskHandlesNullAndEmpty() {
        assertThat(TokenMasker.mask(null)).isEqualTo("<null>");
        assertThat(TokenMasker.mask("")).isEqualTo("<empty>");
    }

    @Test
    @DisplayName("scrub: 자유 형식 문장 안의 Meta 토큰을 지운다")
    void scrubRemovesMetaTokenFromFreeText() {
        String message = "GET https://graph.instagram.com/access_token?access_token="
                + META_TOKEN + " 요청이 실패했습니다";

        String scrubbed = TokenMasker.scrub(message);

        assertThat(scrubbed).doesNotContain(META_TOKEN);
        assertThat(scrubbed).contains("<redacted-token>");
        // 조사에 필요한 나머지 맥락은 유지되어야 한다
        assertThat(scrubbed).contains("graph.instagram.com");
        assertThat(scrubbed).contains("요청이 실패했습니다");
    }

    @Test
    @DisplayName("scrub: 문장 안의 JWT 도 지운다")
    void scrubRemovesJwtFromFreeText() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r0W1";
        String scrubbed = TokenMasker.scrub("토큰 검증 실패: " + jwt);

        assertThat(scrubbed).doesNotContain(jwt);
        assertThat(scrubbed).contains("<redacted-token>");
    }

    @Test
    @DisplayName("scrub: 토큰이 없는 문장은 그대로 둔다")
    void scrubLeavesCleanTextAlone() {
        String clean = "예약 큐 3건을 조회했습니다";
        assertThat(TokenMasker.scrub(clean)).isEqualTo(clean);
    }
}
