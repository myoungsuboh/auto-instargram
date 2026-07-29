package com.autoinstagram.backend.auth.api;

import com.autoinstagram.backend.auth.jwt.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키를 한곳에서 만든다.
 *
 * <p>SKL-AUTHN-AUTHZ 규칙 1: JWT 는 httpOnly · Secure · SameSite=Strict 쿠키에 저장하고
 * localStorage/sessionStorage 에 저장하지 않는다.
 *
 * <p>속성을 여러 곳에서 각자 지정하면 한 군데서 httpOnly 를 빼먹는 사고가 난다.
 * 그래서 쿠키 생성 경로를 이 클래스로 단일화한다.
 */
@Component
public class AuthCookieFactory {

    private final JwtProperties properties;

    public AuthCookieFactory(JwtProperties properties) {
        this.properties = properties;
    }

    /** 액세스 토큰 쿠키. 모든 API 경로에서 전송되도록 path=/ 로 둔다. */
    public ResponseCookie accessCookie(String token, Duration ttl) {
        return base(JwtProperties.ACCESS_COOKIE, token, ttl, "/");
    }

    /**
     * 갱신 토큰 쿠키.
     * path 를 갱신 엔드포인트로 좁혀, 일반 API 요청에는 갱신 토큰이 아예 실려 나가지 않게 한다
     * (노출 표면을 줄인다).
     */
    public ResponseCookie refreshCookie(String token, Duration ttl) {
        return base(JwtProperties.REFRESH_COOKIE, token, ttl, REFRESH_PATH);
    }

    /** 로그아웃 시 쿠키를 즉시 만료시킨다. 값도 비운다. */
    public ResponseCookie expiredAccessCookie() {
        return base(JwtProperties.ACCESS_COOKIE, "", Duration.ZERO, "/");
    }

    public ResponseCookie expiredRefreshCookie() {
        return base(JwtProperties.REFRESH_COOKIE, "", Duration.ZERO, REFRESH_PATH);
    }

    /** 갱신 토큰이 전송될 유일한 경로. */
    static final String REFRESH_PATH = "/api/v1/auth";

    private ResponseCookie base(String name, String value, Duration ttl, String path) {
        return ResponseCookie.from(name, value)
                // JavaScript 가 읽을 수 없게 한다 — XSS 로 토큰이 탈취되지 않는 핵심 속성
                .httpOnly(true)
                // 로컬 개발(http)에서는 false 여야 브라우저가 쿠키를 보낸다.
                // 운영에서는 반드시 true — JWT_COOKIE_SECURE=true (Phase 5 에서 재확인)
                .secure(properties.cookieSecure())
                // CSRF 방어: 다른 사이트에서 시작된 요청에는 쿠키를 보내지 않는다
                .sameSite(properties.cookieSameSite())
                .path(path)
                .maxAge(ttl)
                .build();
    }
}
