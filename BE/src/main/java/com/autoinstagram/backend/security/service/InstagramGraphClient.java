package com.autoinstagram.backend.security.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.common.util.TokenMasker;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Meta Instagram Graph API 호출 (API-05 토큰 교환의 외부 의존 부분).
 *
 * <p><b>재시도를 하지 않는 이유</b> — 공통 규칙 "재시도하려면 멱등성이 필요하다".
 * 단기 토큰 교환은 멱등이 아니다: 교환이 성공했는데 응답만 유실된 경우 재시도하면
 * 이미 소비된 토큰으로 다시 요청하게 되고, 발급된 장기 토큰을 잃어버린다.
 * 따라서 한 번만 시도하고, 실패는 사용자에게 정확히 알린다.
 * (자동 갱신 배치처럼 안전하게 재시도할 수 있는 경로는 이번 설계 범위에 없다)
 *
 * <p>타임아웃은 POL-04(응답 3초 이내) 안에 들어오도록 연결 + 응답 합계를 2.5초 이내로 잡는다.
 */
@Component
public class InstagramGraphClient {

    private static final Logger log = LoggerFactory.getLogger(InstagramGraphClient.class);

    private final InstagramProperties properties;
    private final RestClient restClient;

    public InstagramGraphClient(InstagramProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.graphBaseUrl())
                .requestFactory(timeoutFactory(properties.connectTimeout(), properties.readTimeout()))
                .build();
    }

    private static SimpleClientHttpRequestFactory timeoutFactory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 무한 대기는 자원 고갈로 이어진다 (공통 규칙: 외부·네트워크 호출엔 타임아웃 필수)
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }

    /**
     * 단기 토큰을 장기 액세스 토큰으로 교환한다.
     *
     * @param shortLivedToken 사용자가 넘긴 단기 토큰
     * @return 장기 토큰과 만료까지 남은 초
     * @throws ApiException 설정 누락(422 UNPROCESSABLE) / 토큰이 잘못됨(422 INVALID_TOKEN) /
     *                      상대 서비스 장애·타임아웃(502 UPSTREAM_UNAVAILABLE).
     *                      Meta 의 4xx 를 502 로 올리지 않는 것이 중요하다 — 그러면 화면이
     *                      "잠시 후 다시 시도" 를 안내해 사용자가 같은 잘못된 값으로 반복하게 된다.
     */
    public ExchangedToken exchangeForLongLivedToken(String shortLivedToken) {
        if (!properties.isConfigured()) {
            // SKL-ERROR-HANDLING-RESILIENCE 규칙 2: 설정 누락은 프로그래밍/구성 오류 → fail-fast
            throw new ApiException(ErrorCode.UNPROCESSABLE,
                    "INSTAGRAM_CLIENT_SECRET 이 설정되지 않아 토큰을 교환할 수 없습니다");
        }

        try {
            GraphTokenResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/access_token")
                            .queryParam("grant_type", "ig_exchange_token")
                            .queryParam("client_secret", properties.clientSecret())
                            .queryParam("access_token", shortLivedToken)
                            .build())
                    .retrieve()
                    .body(GraphTokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_TOKEN,
                        "Graph API 가 액세스 토큰을 반환하지 않음");
            }
            if (response.expiresIn() == null || response.expiresIn() <= 0) {
                throw new ApiException(ErrorCode.INVALID_TOKEN,
                        "Graph API 응답의 expires_in 이 유효하지 않음: " + response.expiresIn());
            }

            // 성공 로그에도 토큰을 남기지 않는다 (POL-05)
            log.info("인스타그램 장기 토큰 교환 성공 — 만료까지 {}초", response.expiresIn());
            return new ExchangedToken(response.accessToken(), response.expiresIn());

        } catch (ApiException ex) {
            throw ex;

        } catch (HttpClientErrorException ex) {
            // Meta 가 4xx 를 준 것은 <b>우리가 보낸 값이 잘못됐다</b>는 뜻이다 — 상대 서비스 장애가 아니다.
            // 이것을 502 UPSTREAM_UNAVAILABLE 로 알리면 화면이 "잠시 후 다시 시도해 주세요" 를 띄우고,
            // 사용자는 같은 잘못된 값으로 영원히 재시도하게 된다(실제로 그렇게 신고받았다).
            // 명세(1_spack.md API-05)가 규정한 422 INVALID_TOKEN 으로 알린다.
            String safe = TokenMasker.scrub(ex.getMessage());
            log.warn("인스타그램 토큰 교환 거부 — 보낸 값이 잘못됨: {}", safe);
            throw new ApiException(ErrorCode.INVALID_TOKEN, explainRejection(safe), ex);

        } catch (RestClientException ex) {
            // 여기까지 온 것은 연결 실패·타임아웃·5xx 등 <b>상대 쪽 문제</b>다 — 재시도가 의미 있다.
            // 예외 메시지에 요청 URL 이 담기고 그 안에 토큰·시크릿이 들어 있을 수 있으므로 반드시 scrub 한다.
            String safe = TokenMasker.scrub(ex.getMessage());
            log.error("인스타그램 Graph API 호출 실패: {}", safe);
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Graph API 호출 실패: " + safe, ex);
        }
    }

    /**
     * Meta 의 거부 응답을 <b>사용자가 무엇을 고쳐야 하는지</b> 알 수 있는 문장으로 바꾼다.
     *
     * <p>원문(예: {@code "Invalid OAuth access token - Cannot parse access token"})만 그대로
     * 보여 주면 비개발자는 무엇을 해야 할지 알 수 없다. 실제로 겪은 두 경우를 짚어 준다.
     *
     * <p>package-private 인 이유: 이 문장이 사용자가 실제로 읽는 내용이라 회귀를 막아야 하는데,
     * 외부 호출 없이 검증할 수 있도록 테스트에서 직접 부른다. 새 테스트 의존성을 들이지 않기 위한
     * 선택이다(이 프로젝트는 의존성 추가를 최소화해 왔다 — ADR-0008 참고).
     *
     * @param safeMessage 이미 {@link TokenMasker#scrub} 을 거친 메시지
     */
    static String explainRejection(String safeMessage) {
        String text = safeMessage == null ? "" : safeMessage;

        // 값 자체가 토큰 형태가 아닐 때 Meta 가 쓰는 표현.
        // 앱 시크릿(32자)이나 앱 ID 를 토큰 칸에 붙여넣는 실수가 가장 흔하다.
        if (text.contains("Cannot parse access token")) {
            return "입력한 값이 액세스 토큰 형식이 아닙니다. "
                    + "'Instagram 앱 시크릿' 이나 '앱 ID' 를 붙여넣지 않았는지 확인해 주세요 — "
                    + "인스타그램 토큰은 보통 150자가 넘습니다. "
                    + "토큰은 Meta 앱 대시보드의 'Instagram 로그인이 포함된 API 설정' → "
                    + "'2. 액세스 토큰 생성' → [토큰 생성] 에서 받습니다.";
        }
        // 단기 토큰은 1시간만 유효하다.
        if (text.contains("expired") || text.contains("Session has expired")) {
            return "토큰이 이미 만료되었습니다. 단기 토큰은 발급 후 1시간만 유효하므로, "
                    + "Meta 앱 대시보드에서 [토큰 생성] 을 다시 눌러 새로 받은 뒤 바로 갱신해 주세요.";
        }
        return "인스타그램이 이 토큰을 거부했습니다: " + text;
    }

    /**
     * 토큰의 주인인 인스타그램 계정 정보를 조회한다 (ADR-0024).
     *
     * <p>공식 문서의 호출 형태:
     * {@code GET https://graph.instagram.com/v25.0/me?fields=user_id,username&access_token=...}
     *
     * <p><b>실패를 예외로 올리지 않고 empty 를 돌려주는 이유</b>: 이 조회는 부가 정보다.
     * 호출자는 이미 성공한 토큰 교환의 결과를 손에 들고 있고, 교환은 비멱등이라
     * 되돌리거나 다시 시도할 수 없다(ADR-0009). 조회가 실패했다고 트랜잭션을
     * 롤백해 토큰을 버리면 사용자는 단기 토큰을 다시 발급받아야 한다 — 손실이 훨씬 크다.
     * 값이 없으면 게시 시 {@code INSTAGRAM_USER_ID} 환경변수로 대체된다.
     *
     * @param accessToken 유효한 액세스 토큰
     * @return 계정 정보. 조회에 실패하면 {@link Optional#empty()}
     */
    public Optional<AccountInfo> fetchAccountInfo(String accessToken) {
        try {
            GraphMeResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + properties.apiVersion() + "/me")
                            .queryParam("fields", "user_id,username")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .body(GraphMeResponse.class);

            if (response == null || response.userId() == null || response.userId().isBlank()) {
                log.warn("계정 정보 조회 응답에 user_id 가 없음 — 환경변수 INSTAGRAM_USER_ID 로 대체됩니다");
                return Optional.empty();
            }
            log.info("계정 정보 조회 성공 — userId={}, username={}", response.userId(), response.username());
            return Optional.of(new AccountInfo(response.userId(), response.username()));

        } catch (RestClientException ex) {
            // 메시지에 요청 URL 이 담기고 그 안에 토큰이 있으므로 반드시 scrub 한다 (POL-05)
            log.warn("계정 정보 조회 실패 — 토큰 교환은 유지하고 환경변수로 대체합니다: {}",
                    TokenMasker.scrub(ex.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * 계정 정보. 둘 다 비밀이 아니다 — 토큰과 달리 로그에 남겨도 된다.
     *
     * @param userId   게시 대상 식별에 쓰는 계정 번호
     * @param username 화면 표시용 계정 이름. 사용자가 바꿀 수 있으므로 식별에 쓰지 않는다
     */
    public record AccountInfo(String userId, String username) {
    }

    /** {@code /me} 응답 형태. */
    private record GraphMeResponse(
            @JsonProperty("user_id") String userId,
            @JsonProperty("username") String username
    ) {
    }

    /** 교환 결과. */
    public record ExchangedToken(String accessToken, long expiresInSeconds) {
        /** 토큰이 로그로 새지 않게 한다. */
        @Override
        public String toString() {
            return "ExchangedToken{expiresInSeconds=" + expiresInSeconds + ", accessToken=***}";
        }
    }

    /** Graph API 의 응답 형태 (snake_case → record 매핑). */
    private record GraphTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }
}
