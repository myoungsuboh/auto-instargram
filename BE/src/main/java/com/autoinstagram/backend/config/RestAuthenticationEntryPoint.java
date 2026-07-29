package com.autoinstagram.backend.config;

import com.autoinstagram.backend.common.error.ApiErrorResponse;
import com.autoinstagram.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
// Spring Boot 4 는 Jackson 3 을 기본으로 자동 설정한다 (tools.jackson.*).
// Jackson 2 의 com.fasterxml.jackson.databind.ObjectMapper 는 전이 의존성으로만 존재하고
// 빈으로 등록되지 않으므로, 주입받으려 하면 NoSuchBeanDefinitionException 이 난다.
import tools.jackson.databind.ObjectMapper;

/**
 * 인증·인가 실패를 1_spack.md 의 에러 표 형식으로 응답한다.
 *
 * <p>Spring Security 의 기본 동작은 HTML 로그인 페이지로 리다이렉트하거나 빈 401 을 주는데,
 * 명세는 {@code {"code":"AUTH_REQUIRED","message":"인증이 필요합니다"}} 형태의 JSON 을 요구한다.
 * 화면이 이 코드로 분기하므로 형식이 어긋나면 계약 위반이다.
 *
 * <p>한 클래스가 두 인터페이스를 구현한다 — 401(미인증)과 403(권한 부족)은
 * 응답 코드만 다르고 형식이 같아서, 형식을 한곳에서 관리하는 편이 어긋날 여지가 없다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401 — JWT 누락 또는 만료. */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 요청 경로만 남긴다. 토큰이나 헤더 원문은 남기지 않는다 (POL-05)
        log.warn("[AUTH_REQUIRED] 401 — 인증 없이 {} {} 접근", request.getMethod(), request.getRequestURI());
        write(response, ErrorCode.AUTH_REQUIRED);
    }

    /** 403 — 인증은 됐지만 권한이 부족. */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[FORBIDDEN] 403 — 권한 부족으로 {} {} 거부", request.getMethod(), request.getRequestURI());
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(errorCode));
    }
}
