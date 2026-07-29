package com.autoinstagram.backend.auth.api;

import com.autoinstagram.backend.auth.api.dto.LoginRequest;
import com.autoinstagram.backend.auth.api.dto.SessionResponse;
import com.autoinstagram.backend.auth.domain.AppAccount;
import com.autoinstagram.backend.auth.jwt.JwtProperties;
import com.autoinstagram.backend.auth.jwt.JwtTokenProvider;
import com.autoinstagram.backend.auth.service.AuthService;
import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 로그인 API.
 *
 * <p>⚠️ 1_spack.md 에 없는 추가 엔드포인트 4개다 (사용자 확정 결정).
 * 근거: docs/decisions/2026-07-29-dashboard-login-added.md
 * 계약: _workspace/api_contracts.md
 *
 * <p>토큰은 응답 바디가 아니라 httpOnly 쿠키로만 전달한다 (SKL-AUTHN-AUTHZ 규칙 1).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;

    public AuthController(AuthService authService, AuthCookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    /** 로그인. 성공하면 액세스·갱신 토큰을 쿠키로 심는다. */
    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletRequest httpRequest) {
        var tokens = authService.login(request.username(), request.password(), clientIp(httpRequest));
        return withAuthCookies(tokens);
    }

    /**
     * 액세스 토큰 재발급 (SKL-AUTHN-AUTHZ 규칙 2).
     *
     * <p>이 엔드포인트는 인증 없이 접근 가능해야 한다 — 액세스 토큰이 이미 만료된 상태에서
     * 호출되는 것이 정상 흐름이기 때문이다. 대신 갱신 토큰 쿠키가 신원을 증명한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = JwtProperties.REFRESH_COOKIE, required = false) String refreshToken) {
        var tokens = authService.refresh(refreshToken);
        return withAuthCookies(tokens);
    }

    /** 로그아웃. 서버의 갱신 토큰을 폐기하고 브라우저 쿠키도 만료시킨다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedUser user) {
        if (user != null) {
            authService.logout(user.accountId());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredRefreshCookie().toString())
                .build();
    }

    /**
     * 현재 로그인 상태 조회.
     *
     * <p>화면은 httpOnly 쿠키를 읽을 수 없으므로, 로그인되어 있는지 판단할 방법이 필요하다.
     * 이 엔드포인트가 그 역할을 한다.
     */
    @GetMapping("/me")
    public SessionResponse me(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "인증 주체 없음");
        }
        AppAccount account = authService.requireAccount(user.accountId());
        // 전체 수명(900)이 아니라 실제 남은 시간을 준다 — 화면이 이 값으로 자동 갱신 시점을 잡으므로,
        // 만료 직전에도 900 을 돌려주면 화면이 갱신을 미루다 요청이 401 로 실패한다.
        return SessionResponse.of(account.getUsername(), account.getRole(), user.remainingSeconds());
    }

    private ResponseEntity<SessionResponse> withAuthCookies(AuthService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.accessCookie(tokens.accessToken(), tokens.accessTtl()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.refreshCookie(tokens.refreshToken(), tokens.refreshTtl()).toString())
                .body(SessionResponse.of(tokens.username(), tokens.role(), tokens.accessTtl().toSeconds()));
    }

    /**
     * 클라이언트 IP. 리버스 프록시 뒤라면 X-Forwarded-For 의 첫 항목이 원 클라이언트다.
     *
     * <p>주의: 이 헤더는 클라이언트가 위조할 수 있다. 그래서 IP 제한은 보조 방어로만 쓰고,
     * 진짜 방어선은 위조할 수 없는 계정 단위 잠금(DB)에 둔다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
