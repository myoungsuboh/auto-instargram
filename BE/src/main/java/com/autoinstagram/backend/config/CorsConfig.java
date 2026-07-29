package com.autoinstagram.backend.config;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 설정 — 3_architecture.md §4 Connection Map
 * (Mobile Web Frontend → Instagram Automation Backend, HTTPS/REST).
 *
 * <p>프론트엔드(기본 localhost:5173)와 백엔드(8080)는 포트가 달라 브라우저가 교차 출처로 본다.
 * 이 설정이 없으면 화면의 모든 API 호출이 브라우저 단계에서 차단된다.
 *
 * <p><b>허용 출처를 반드시 특정해야 한다</b> — [ADR-0006] 이 명시한 제약이다.
 * 이 API 는 CSRF 토큰 대신 세 겹으로 방어한다:
 * <ol>
 *   <li>인증 쿠키의 {@code SameSite=Strict}</li>
 *   <li>JSON 전용 API (폼 전송으로 호출 불가)</li>
 *   <li><b>CORS 허용 출처 제한</b> ← 이 클래스</li>
 * </ol>
 * 3번을 {@code *} 로 열면 나머지 방어가 함께 약해진다.
 *
 * <p>또한 쿠키를 주고받으려면 {@code allowCredentials=true} 가 필요하고,
 * 그 경우 브라우저 표준이 {@code allowedOrigins="*"} 를 금지한다 —
 * 즉 와일드카드를 쓰면 인증 자체가 동작하지 않는다.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = List.of(allowedOrigins.split("\\s*,\\s*"));

        // 와일드카드가 설정으로 흘러 들어오는 것을 기동 시점에 막는다 (fail-fast).
        // 운영에서 실수로 * 를 넣으면 CSRF 방어가 조용히 약해지고 쿠키 인증도 깨진다.
        if (this.allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS 에 와일드카드(*)를 쓸 수 없습니다. "
                            + "쿠키 인증(allowCredentials)과 함께 쓸 수 없고, "
                            + "CSRF 방어(ADR-0006)의 한 축이 무너집니다. "
                            + "허용할 출처를 명시하세요 (예: http://localhost:5173).");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                // 명세 외 추가 API 의 인증 경로 (ADR-0006: Bearer 헤더도 받는다)
                "Authorization",
                // 멱등성 헤더 (skills/backEnd/idempotency-idempotency.md 규칙 1)
                "Idempotency-Key"));

        // httpOnly 쿠키를 주고받기 위해 필수. 이게 false 면 화면이 로그인 상태를 유지할 수 없다.
        configuration.setAllowCredentials(true);

        // 사전 요청(preflight) 결과를 캐시해 왕복을 줄인다 (POL-04 응답 시간에 도움)
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS 허용 출처: {}", allowedOrigins);
        return source;
    }
}
