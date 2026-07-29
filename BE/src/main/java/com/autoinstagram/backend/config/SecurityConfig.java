package com.autoinstagram.backend.config;

import com.autoinstagram.backend.auth.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인증·인가 설정. SKL-AUTHN-AUTHZ 규칙 3(권한 검증은 반드시 서버 측에서)의 구현 지점이다.
 *
 * <p>경로별 권한은 1_spack.md 각 API 의 {@code required_roles} 를 그대로 옮겼다:
 * <table>
 *   <tr><th>API</th><th>required_roles</th></tr>
 *   <tr><td>POST /api/v1/queues</td><td>system_admin, system_operator</td></tr>
 *   <tr><td>GET /api/v1/queues</td><td>system_operator, system_admin</td></tr>
 *   <tr><td>GET /api/v1/history</td><td>system_operator, system_admin</td></tr>
 *   <tr><td>POST /api/v1/reels/upload</td><td>system_operator, system_admin</td></tr>
 *   <tr><td>POST /api/v1/tokens/refresh</td><td><b>system_admin 만</b></td></tr>
 * </table>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ── CSRF ──────────────────────────────────────────────────
                // 토큰 방식 CSRF 를 쓰지 않는 근거: 이 API 는 폼이 아니라 JSON 전용이고,
                // 인증 쿠키에 SameSite=Strict 를 걸어 다른 사이트에서 시작된 요청에는
                // 쿠키가 아예 실리지 않는다(SKL-AUTHN-AUTHZ 규칙 1 이 지정한 방어).
                // 여기에 Phase 5 의 CORS 허용 출처 제한이 더해진다.
                .csrf(csrf -> csrf.disable())

                // JWT 기반이므로 서버 세션을 만들지 않는다
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 기본 로그인 화면·HTTP Basic 을 끈다 — 이 서비스는 JSON API 로만 인증한다
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())

                // 401·403 을 1_spack.md 의 에러 형식(JSON)으로 응답한다
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAuthenticationEntryPoint))

                .authorizeHttpRequests(auth -> auth
                        // ── 인증 없이 접근 가능 ────────────────────────────
                        // 로그인·갱신은 아직 토큰이 없는(또는 만료된) 상태에서 호출된다
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        // 3_architecture.md SVC-02 Health check: /actuator/health
                        // (운영 모니터링이 토큰 없이 호출해야 하는 엔드포인트)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // CORS 사전 요청(preflight)에는 인증 헤더가 실리지 않는다
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── API-05: 시스템 관리자만 ────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/tokens/refresh")
                        .hasRole("SYSTEM_ADMIN")

                        // ── API-01~04: 운영자 또는 관리자 ──────────────────
                        .requestMatchers("/api/v1/queues/**", "/api/v1/queues",
                                "/api/v1/history/**", "/api/v1/history",
                                "/api/v1/reels/**")
                        .hasAnyRole("SYSTEM_ADMIN", "SYSTEM_OPERATOR")

                        // ── 그 밖의 모든 요청은 인증 필요 ──────────────────
                        // 새 엔드포인트를 추가했을 때 실수로 공개되지 않도록 기본값을 닫아 둔다
                        // (fail-closed — SKL-INPUT-VALIDATION 규칙 1)
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 비밀번호 해시. BCrypt 는 의도적으로 느려 무차별 대입을 어렵게 한다.
     * strength 12 — 기본값 10보다 한 단계 높여 두었다(로그인 빈도가 낮은 관리 도구라 부담이 적다).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
