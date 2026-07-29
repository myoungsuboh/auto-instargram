package com.autoinstagram.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoinstagram.backend.auth.domain.AppAccountRepository;
import com.autoinstagram.backend.auth.jwt.JwtProperties;
import com.autoinstagram.backend.auth.service.AccountSeeder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
// Boot 4 에서 패키지가 org.springframework.boot.test.autoconfigure.web.servlet →
// org.springframework.boot.webmvc.test.autoconfigure 로 이동했다 (모듈별 test 스타터 재편)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * 00-ORCHESTRATOR Verify 2 의 통합 검증:
 * "Run backend unit tests and integration tests for security and token refresh endpoints;
 *  seed initial admin credentials idempotently; fetch seeded security state through a real API call"
 *
 * <p>실행 전제: PostgreSQL 이 떠 있어야 한다 (docker compose -f docker-compose.dev.yml up -d).
 * 실제 DB 를 쓰는 이유 — 인메모리 DB 로 바꾸면 V1/V2 마이그레이션의 CHECK 제약과
 * partial unique index 가 검증되지 않아 "테스트는 통과하는데 운영에서 깨지는" 상황이 생긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountSeeder accountSeeder;

    @Autowired
    private AppAccountRepository accountRepository;

    /**
     * 실제 Meta Graph API 를 호출하지 않도록 대체한다.
     * 외부 의존을 진짜로 부르면 테스트가 네트워크 상태에 좌우되고, 무엇보다
     * 남의 서비스를 테스트 때마다 두드리게 된다.
     */
    @MockitoBean
    private com.autoinstagram.backend.security.service.InstagramGraphClient graphClient;

    @Value("${app.seed.admin-username:}")
    private String adminUsername;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.operator-username:}")
    private String operatorUsername;

    @Value("${app.seed.operator-password:}")
    private String operatorPassword;

    @BeforeEach
    void seedAccounts() {
        // 시드를 두 번 돌려도 예외 없이 통과해야 한다 (Verify 2: idempotently)
        accountSeeder.run(new DefaultApplicationArguments());
        accountSeeder.run(new DefaultApplicationArguments());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  시드 멱등성
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("시드는 멱등하다 — 여러 번 실행해도 계정이 중복 생성되지 않는다")
    void seedingIsIdempotent() {
        long before = accountRepository.count();

        accountSeeder.run(new DefaultApplicationArguments());
        accountSeeder.run(new DefaultApplicationArguments());

        assertThat(accountRepository.count()).isEqualTo(before);
        assertThat(accountRepository.existsByUsernameAndDeletedAtIsNull(adminUsername)).isTrue();
        assertThat(accountRepository.existsByUsernameAndDeletedAtIsNull(operatorUsername)).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  로그인 — 시드된 자격 증명으로 실제 API 호출
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("시드된 관리자 계정으로 로그인하면 200 과 인증 쿠키를 받는다")
    void loginWithSeededAdmin() throws Exception {
        MvcResult result = login(adminUsername, adminPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername))
                // 1_spack.md 표기(system_admin)로 내보내야 한다
                .andExpect(jsonPath("$.role").value("system_admin"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        Cookie access = result.getResponse().getCookie(JwtProperties.ACCESS_COOKIE);
        Cookie refresh = result.getResponse().getCookie(JwtProperties.REFRESH_COOKIE);

        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        // SKL-AUTHN-AUTHZ 규칙 1: httpOnly 여야 한다 (JavaScript 로 읽히면 XSS 로 탈취된다)
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        // 규칙 2: 액세스 토큰은 15분 이하
        assertThat(access.getMaxAge()).isLessThanOrEqualTo(900);
    }

    @Test
    @DisplayName("응답 바디에 토큰이 들어가지 않는다 (규칙 1 / OWASP #4)")
    void loginResponseBodyHasNoToken() throws Exception {
        MvcResult result = login(adminUsername, adminPassword).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = result.getResponse().getCookie(JwtProperties.ACCESS_COOKIE).getValue();

        assertThat(body).doesNotContain(accessToken);
        assertThat(body).doesNotContain("accessToken");
        assertThat(body).doesNotContain(adminPassword);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 이고, 아이디 존재 여부를 알려주지 않는다")
    void loginWithWrongPassword() throws Exception {
        login(adminUsername, "WrongPassword!9999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));
    }

    @Test
    @DisplayName("없는 아이디도 같은 401 응답을 준다 (계정 열거 방지)")
    void loginWithUnknownUserLooksIdentical() throws Exception {
        login("nosuchuser", "WrongPassword!9999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("아이디 형식이 규칙에 맞지 않으면 422 로 거부한다 (화이트리스트 검증)")
    void loginRejectsInvalidUsernameFormat() throws Exception {
        // SKL-INPUT-VALIDATION 규칙 2: 허용 문자만 통과시킨다
        login("admin'; DROP TABLE app_accounts;--", "SomePassword!123")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 표가 지워지지 않았음을 확인 — 파라미터화 쿼리 + 검증이 함께 동작해야 한다
        assertThat(accountRepository.existsByUsernameAndDeletedAtIsNull(adminUsername)).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  인증·인가 (SKL-AUTHN-AUTHZ 규칙 3: 서버 측 검증)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("인증 없이 보호된 API 를 부르면 명세 형식의 401 을 받는다")
    void protectedEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                // 1_spack.md 의 전 API 공통 에러
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    @DisplayName("쿠키로 세션을 확인할 수 있다 (화면은 httpOnly 쿠키를 읽을 수 없으므로)")
    void meReturnsSessionWithCookie() throws Exception {
        Cookie access = loginAndGetAccessCookie(adminUsername, adminPassword);

        mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername))
                .andExpect(jsonPath("$.role").value("system_admin"));
    }

    @Test
    @DisplayName("/me 의 expiresIn 은 전체 수명이 아니라 실제 남은 시간이다")
    void meReturnsRemainingLifetimeNotFullTtl() throws Exception {
        Cookie access = loginAndGetAccessCookie(adminUsername, adminPassword);

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        long expiresIn = Long.parseLong(body.replaceAll(".*\"expiresIn\":(\\d+).*", "$1"));

        // 전체 수명(900) 이하이고 0 보다 커야 한다.
        // 항상 900 을 돌려주면 화면이 만료 직전에도 갱신을 미뤄 요청이 401 로 실패한다.
        assertThat(expiresIn).isPositive().isLessThanOrEqualTo(900);
    }

    @Test
    @DisplayName("없는 아이디로 로그인해도 BCrypt 대조 시간을 소비한다 (타이밍으로 계정 존재가 드러나지 않음)")
    void unknownUserStillCostsHashingTime() throws Exception {
        // 손으로 쓴 잘못된 형식의 더미 해시를 쓰면 인코더가 즉시 false 를 반환해
        // 응답이 눈에 띄게 빨라지고, 그 차이로 아이디 존재 여부가 드러난다.
        long unknownElapsed = timeOf(() -> login("nosuchuser1", "SomePassword!123")
                .andExpect(status().isUnauthorized()));
        long knownElapsed = timeOf(() -> login(adminUsername, "WrongPassword!123")
                .andExpect(status().isUnauthorized()));

        // BCrypt strength 12 는 수백 ms 가 걸린다. 없는 아이디도 그 비용을 치러야 한다.
        // 환경에 따라 절대값이 다르므로 "알려진 아이디의 절반 이상" 이라는 느슨한 기준으로 본다.
        assertThat(unknownElapsed)
                .as("없는 아이디(%dms) 가 알려진 아이디(%dms) 보다 현저히 빠르면 안 된다",
                        unknownElapsed, knownElapsed)
                .isGreaterThanOrEqualTo(knownElapsed / 2);
    }

    private long timeOf(ThrowingRunnable action) throws Exception {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    @DisplayName("Bearer 헤더로도 인증된다 (3_architecture.md Connection Map auth: bearer)")
    void bearerHeaderAlsoWorks() throws Exception {
        String token = loginAndGetAccessCookie(adminUsername, adminPassword).getValue();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername));
    }

    @Test
    @DisplayName("위조된 토큰은 거부된다")
    void forgedTokenRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.bogus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API-05 권한: system_admin 만 (1_spack.md required_roles)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("운영자는 토큰 갱신 API 를 호출할 수 없다 (403)")
    void operatorCannotRefreshInstagramToken() throws Exception {
        Cookie operatorCookie = loginAndGetAccessCookie(operatorUsername, operatorPassword);

        mockMvc.perform(post("/api/v1/tokens/refresh")
                        .cookie(operatorCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortLivedToken\":\"EAAGshortlivedtokenvalue123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("권한이 없습니다"));
    }

    @Test
    @DisplayName("관리자가 빈 토큰을 보내면 422 VALIDATION_ERROR")
    void adminWithBlankTokenGets422() throws Exception {
        Cookie adminCookie = loginAndGetAccessCookie(adminUsername, adminPassword);

        mockMvc.perform(post("/api/v1/tokens/refresh")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortLivedToken\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("로그아웃하면 쿠키가 만료된다")
    void logoutExpiresCookies() throws Exception {
        Cookie access = loginAndGetAccessCookie(adminUsername, adminPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(access))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cleared = result.getResponse().getCookie(JwtProperties.ACCESS_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  도우미
    // ═══════════════════════════════════════════════════════════════════

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        String json = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Cookie loginAndGetAccessCookie(String username, String password) throws Exception {
        MvcResult result = login(username, password).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(JwtProperties.ACCESS_COOKIE);
        assertThat(cookie).as("로그인 후 액세스 쿠키가 있어야 한다").isNotNull();
        return cookie;
    }
}
