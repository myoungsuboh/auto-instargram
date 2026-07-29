package com.autoinstagram.backend.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 및 인증 쿠키 설정.
 *
 * <p>SKL-AUTHN-AUTHZ 규칙 2(Access Token 15분 이하 + Refresh Token 자동 갱신)의 수치를 여기서 고정한다.
 * SKL-SECRETS-MANAGEMENT 규칙 1에 따라 {@code secret} 은 코드에 없고 환경변수 {@code JWT_SECRET} 에서 주입된다.
 *
 * @param secret          HS256 서명 키. 최소 32바이트. 짧으면 기동 시 거부한다(fail-fast).
 * @param issuer          토큰 발급자 식별자
 * @param accessTokenTtl  액세스 토큰 수명 — 규칙 2 에 따라 15분을 넘길 수 없다
 * @param refreshTokenTtl 갱신 토큰 수명
 * @param cookieSecure    쿠키 Secure 속성. HTTPS 가 아닌 로컬 개발에서는 false 여야 브라우저가 쿠키를 보낸다.
 *                        운영에서는 반드시 true (Phase 5 전송 보안에서 재확인)
 * @param cookieSameSite  쿠키 SameSite 속성. 규칙 1 은 Strict 를 요구한다
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure,
        String cookieSameSite
) {

    /** SKL-AUTHN-AUTHZ 규칙 2: Access Token 만료 시간은 15분 이하. */
    public static final Duration MAX_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    /** HS256 이 요구하는 최소 키 길이. */
    public static final int MIN_SECRET_BYTES = 32;

    /** 액세스 토큰을 담는 httpOnly 쿠키 이름. */
    public static final String ACCESS_COOKIE = "ai_access";

    /** 갱신 토큰을 담는 httpOnly 쿠키 이름. */
    public static final String REFRESH_COOKIE = "ai_refresh";
}
