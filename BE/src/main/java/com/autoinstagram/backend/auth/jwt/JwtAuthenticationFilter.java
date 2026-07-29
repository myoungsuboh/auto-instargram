package com.autoinstagram.backend.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 액세스 토큰을 확인해 인증 주체를 세운다.
 *
 * <p><b>토큰을 두 곳에서 받는 이유</b> — 설계 문서와 코딩 규칙이 서로 다른 것을 요구한다:
 * <ul>
 *   <li>3_architecture.md Connection Map: FE → BE 인증은 {@code bearer}
 *       → {@code Authorization: Bearer ...} 헤더</li>
 *   <li>skills/security/JWT-authn-authz.md 규칙 1: JWT 를 httpOnly 쿠키에 저장하고
 *       localStorage/sessionStorage 에 저장하지 않는다</li>
 * </ul>
 * 둘 다 지키기 위해 <b>쿠키를 우선</b>으로 읽고, 없으면 Bearer 헤더로 넘어간다.
 * 브라우저(화면)는 쿠키만 쓰므로 JavaScript 가 토큰을 만질 일이 없어 XSS 로 탈취되지 않는다.
 * curl 같은 프로그램 클라이언트는 Bearer 헤더를 쓸 수 있어 명세의 계약도 유지된다.
 *
 * <p>SKL-AUTHN-AUTHZ 규칙 3: 권한 검증은 서버에서 한다. 이 필터가 그 입구다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 이미 인증된 요청은 건드리지 않는다
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(tokenProvider::verify)
                    .ifPresent(this::setAuthentication);
        }
        // 토큰이 없거나 유효하지 않아도 여기서 401 을 내지 않는다.
        // 인증이 필요한 경로인지는 SecurityConfig 가 판단하고,
        // 거부 응답은 RestAuthenticationEntryPoint 가 명세 형식으로 만든다.
        filterChain.doFilter(request, response);
    }

    /** 쿠키 우선, 없으면 Authorization 헤더. */
    private Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JwtProperties.ACCESS_COOKIE.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            if (!value.isEmpty()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private void setAuthentication(JwtTokenProvider.AuthenticatedUser user) {
        var authorities = List.of(new SimpleGrantedAuthority(user.role().getAuthority()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
