package com.autoinstagram.backend.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Meta 가 토큰을 거부했을 때 사용자에게 보여 주는 문장 검증.
 *
 * <p><b>왜 이 테스트가 있는가</b> — 사용자가 "단기 토큰" 칸에 <b>Instagram 앱 시크릿</b>(32자)을
 * 붙여넣었다. Meta 는 400 과 함께
 * {@code "Invalid OAuth access token - Cannot parse access token"} 을 돌려줬는데,
 * 구현이 이것을 502 UPSTREAM_UNAVAILABLE 로 올려 화면에 "외부 서비스와 통신할 수 없습니다.
 * <b>잠시 후 다시 시도해 주세요</b>" 가 떴다. 원인이 자기 입력값인 줄 모르니 같은 값으로
 * 계속 재시도할 수밖에 없는 안내였다.
 *
 * <p>4xx→422 / 5xx→502 분기 자체는 {@code catch} 절 순서로 보장된다 —
 * {@code HttpClientErrorException} 이 {@code RestClientException} 의 하위 타입이라
 * 순서가 뒤바뀌면 컴파일되지 않는다. 여기서는 <b>사용자가 읽는 문장</b>을 지킨다.
 */
class GraphRejectionMessageTest {

    @Test
    @DisplayName("토큰 형식이 아닐 때 — 앱 시크릿·앱 ID 를 넣지 않았는지 짚어 준다")
    void explainsCannotParse() {
        String message = InstagramGraphClient.explainRejection(
                "400 Bad Request: {\"error\":{\"message\":"
                        + "\"Invalid OAuth access token - Cannot parse access token\","
                        + "\"type\":\"OAuthException\",\"code\":190}}");

        assertThat(message)
                .contains("액세스 토큰 형식이 아닙니다")
                // 실제로 사용자가 헷갈린 두 값을 이름으로 짚어야 한다
                .contains("앱 시크릿")
                .contains("앱 ID")
                // 길이로 자기 진단할 수 있게 한다 (토큰은 150자 이상, 앱 시크릿은 32자)
                .contains("150자")
                // 어디서 받는지까지 알려 준다
                .contains("토큰 생성");
        // "다시 시도" 류의 안내를 하면 안 된다 — 같은 값으로 재시도해도 영원히 실패한다
        assertThat(message).doesNotContain("잠시 후");
        assertThat(message).doesNotContain("다시 시도해 주세요");
    }

    @Test
    @DisplayName("만료된 토큰일 때 — 단기 토큰의 1시간 제한을 알려 준다")
    void explainsExpired() {
        String message = InstagramGraphClient.explainRejection(
                "400 Bad Request: {\"error\":{\"message\":"
                        + "\"Error validating access token: Session has expired\"}}");

        assertThat(message).contains("만료").contains("1시간").contains("토큰 생성");
    }

    @Test
    @DisplayName("알 수 없는 거부 사유는 원문을 그대로 전달한다 — 삼키지 않는다")
    void passesThroughUnknownReason() {
        String message = InstagramGraphClient.explainRejection(
                "400 Bad Request: {\"error\":{\"message\":\"Some brand new Meta error\"}}");

        assertThat(message)
                .contains("인스타그램이 이 토큰을 거부했습니다")
                .contains("Some brand new Meta error");
    }

    @Test
    @DisplayName("메시지가 null 이어도 깨지지 않는다")
    void toleratesNull() {
        assertThat(InstagramGraphClient.explainRejection(null))
                .isNotNull()
                .contains("거부했습니다");
    }
}
