package com.autoinstagram.backend.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 전송 보안 설정 — skills/security/HTTPS-transport-security.md 구현.
 *
 * <p>규칙 6("헤더·정책은 한곳에서 일괄 적용")에 따라 엔드포인트마다 흩뿌리지 않고
 * 여기서 한 번에 건다. 규칙 7("설정은 코드가 아닌 환경으로")에 따라 환경별로 다른 값은
 * 환경변수로 분리한다.
 *
 * <p><b>이 API 는 JSON 만 반환하고 HTML 을 서비스하지 않는다</b> — 그래서 헤더 선택이
 * 일반 웹앱과 다르다. 예를 들어 CSP 는 스크립트 실행 맥락이 없어 실효가 작지만,
 * 혹시 브라우저가 응답을 문서로 해석하는 경우를 막기 위해 최소 정책을 건다.
 */
@Configuration
public class TransportSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(TransportSecurityConfig.class);

    /**
     * HSTS 최대 기간. 규칙 2("HSTS로 평문 재접속을 차단한다").
     * 1년은 브라우저 프리로드 목록 등재 요건이기도 하다.
     */
    private static final Duration HSTS_MAX_AGE = Duration.ofDays(365);

    private final boolean httpsEnforced;

    public TransportSecurityConfig(@Value("${app.security.https-enforced:false}") boolean httpsEnforced) {
        this.httpsEnforced = httpsEnforced;
        if (!httpsEnforced) {
            // 로컬 개발에서는 http 로 접속하므로 HSTS·HTTPS 강제를 끈다.
            // 켠 상태로 http 에 접속하면 브라우저가 이후 접속을 전부 https 로 강제해
            // 로컬 개발이 아예 불가능해진다(브라우저 설정을 직접 지워야 풀린다).
            log.warn("""
                    ⚠️  HTTPS 강제와 HSTS 가 꺼져 있습니다 (개발 모드).
                        운영 배포 시 HTTPS_ENFORCED=true 로 설정하세요 —
                        그러지 않으면 인증 쿠키와 토큰이 평문으로 전송됩니다.""");
        }
    }

    /**
     * 보안 헤더를 {@link SecurityConfig} 의 필터 체인에 적용한다.
     *
     * <p>{@link SecurityConfig} 가 이 메서드를 호출한다 — 헤더 설정을 그쪽에 섞어 쓰면
     * 인증 설정과 전송 보안 설정이 한 파일에 뒤엉켜 어느 쪽을 고치는지 알기 어려워진다.
     */
    void apply(HttpSecurity http) throws Exception {
        http.headers(headers -> {
            // ── 클릭재킹 차단 ──────────────────────────────────────────
            // 이 API 를 iframe 에 넣어 감싸는 공격을 막는다. HTML 을 서비스하지 않더라도
            // 오류 응답 등이 문서로 렌더링될 여지를 없앤다.
            headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);

            // ── MIME 스니핑 차단 ──────────────────────────────────────
            // 브라우저가 Content-Type 을 무시하고 내용을 추측해 실행하는 것을 막는다.
            headers.contentTypeOptions(contentType -> {
            });

            // ── 리퍼러 최소화 ─────────────────────────────────────────
            // 다른 사이트로 이동할 때 우리 경로·쿼리가 새지 않게 한다.
            headers.referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));

            // ── CSP: 기본 거부 (규칙 3 default-deny) ───────────────────
            // JSON API 이므로 어떤 리소스도 로드할 필요가 없다. 전부 막는다.
            headers.contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));

            // ── HSTS (규칙 2) ─────────────────────────────────────────
            if (httpsEnforced) {
                headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(HSTS_MAX_AGE.toSeconds()));
            } else {
                // 개발 모드에서는 반드시 꺼야 한다 (위 생성자 주석 참조)
                headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
            }

            // ── 캐시 금지 (규칙 5)는 별도로 설정하지 않는다 ──────────────
            // Spring Security 의 기본 헤더 라이터가 이미 모든 응답에
            //   Cache-Control: no-cache, no-store, max-age=0, must-revalidate
            //   Pragma: no-cache / Expires: 0
            // 을 넣는다(실측 확인). 같은 헤더를 커스텀 필터로 또 쓰면
            // Security 의 라이터가 나중에 덮어써 <b>동작하지 않는 죽은 코드</b>가 된다.
            // 이 API 는 정적 리소스를 서비스하지 않으므로 전 응답 캐시 금지가 그대로 맞다.
        });

        // ── HTTPS 강제 (규칙 1) ───────────────────────────────────────
        if (httpsEnforced) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
    }

    /** 운영에서 HTTPS 가 강제되는지 (README·헬스 점검에서 확인용). */
    public boolean isHttpsEnforced() {
        return httpsEnforced;
    }
}
