package com.autoinstagram.backend.security.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Meta Instagram Graph API 연동 설정
 * (3_architecture.md SVC-02 외부 의존성: "Meta Instagram Graph API — 릴스 게시 및 토큰 관리").
 *
 * @param graphBaseUrl   Graph API 기본 주소
 * @param clientSecret   토큰 교환에 필요한 앱 시크릿. 환경변수에서 주입되며 비어 있을 수 있다
 *                       (미설정 시 인스타그램 연동만 비활성화되고 나머지 기능은 정상 동작)
 * @param apiVersion     버전이 필요한 엔드포인트에 붙이는 접두사 (예: {@code v25.0}).
 *                       {@code /access_token} 은 공식 예시가 버전 없이 호출하므로 붙이지 않지만,
 *                       {@code /me} 는 공식 예시가 {@code /v25.0/me} 로 버전을 붙인다.
 *                       Meta 는 버전을 생략하면 <b>가장 오래된 지원 버전</b>으로 처리할 수 있어,
 *                       버전이 문서에 명시된 호출은 명시된 대로 보낸다.
 * @param connectTimeout 연결 타임아웃 — 공통 규칙: 외부 호출엔 타임아웃 필수
 * @param readTimeout    응답 타임아웃 — POL-04(응답 3초 이내) 안에 들어와야 한다
 */
@ConfigurationProperties(prefix = "app.instagram")
public record InstagramProperties(
        String graphBaseUrl,
        String clientSecret,
        String apiVersion,
        Duration connectTimeout,
        Duration readTimeout
) {

    /** 시크릿이 설정되어 실제 연동이 가능한 상태인지. */
    public boolean isConfigured() {
        return clientSecret != null && !clientSecret.isBlank();
    }
}
