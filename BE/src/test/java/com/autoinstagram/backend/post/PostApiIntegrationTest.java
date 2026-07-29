package com.autoinstagram.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoinstagram.backend.auth.jwt.JwtProperties;
import com.autoinstagram.backend.auth.service.AccountSeeder;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import com.autoinstagram.backend.post.service.MediaPathValidator;
import com.autoinstagram.backend.post.service.PostDataSeeder;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 00-ORCHESTRATOR Verify 3 의 통합 검증:
 * "Run Spring Boot test suite covering <b>queue creation, history lookup, and reels upload</b>;
 *  seed test queue items and history records <b>idempotently</b>"
 *
 * <p>실행 전제: PostgreSQL 이 떠 있어야 한다 (ADR-0010).
 *
 * <p>백그라운드 워커를 끈 이유: 릴스 업로드는 {@code scheduledAt=now} 로 등록되므로
 * 워커가 켜져 있으면 테스트 중에 항목을 집어 가 상태를 바꿔 버린다.
 * 워커 자체의 동작은 이 테스트의 대상이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.publish.worker-enabled=false")
class PostApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountSeeder accountSeeder;

    @Autowired
    private PostDataSeeder postDataSeeder;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private MediaPathValidator pathValidator;

    @Value("${app.seed.admin-username:}")
    private String adminUsername;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    private Cookie authCookie;

    @BeforeEach
    void setUp() throws Exception {
        accountSeeder.run(new DefaultApplicationArguments());
        authCookie = login();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  시드 멱등성 (Verify 3)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("예약·이력 시드는 멱등하다 — 여러 번 실행해도 늘어나지 않는다")
    void seedingIsIdempotent() {
        postDataSeeder.run(new DefaultApplicationArguments());
        long afterFirst = queueItemRepository.countByDeletedAtIsNull();

        postDataSeeder.run(new DefaultApplicationArguments());
        postDataSeeder.run(new DefaultApplicationArguments());

        assertThat(queueItemRepository.countByDeletedAtIsNull()).isEqualTo(afterFirst);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API-01 POST /api/v1/queues — 예약 등록
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("API-01: 예약을 등록하면 201 과 명세대로의 필드를 받는다")
    void createQueueItem() throws Exception {
        postQueue("new-reel.mp4", "테스트 캡션", futureIso())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("API-01: 인증 없이 호출하면 401 AUTH_REQUIRED")
    void createRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueBody("x.mp4", "캡션", futureIso())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("API-01: 미디어 경로가 없으면 422 VALIDATION_ERROR (명세의 에러 표)")
    void createRejectsMissingMediaPath() throws Exception {
        postQueue("", "캡션", futureIso())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("잘못된 입력값입니다"))
                .andExpect(jsonPath("$.fields[0].field").value("mediaPath"));
    }

    @Test
    @DisplayName("API-01: 시간 형식이 잘못되면 400 BAD_REQUEST")
    void createRejectsBadDateFormat() throws Exception {
        postQueue("x.mp4", "캡션", "어제쯤")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("API-01: 경로 탐색 공격을 422 로 차단한다 (SKL-INPUT-VALIDATION 규칙 6)")
    void createBlocksPathTraversal() throws Exception {
        postQueue("../../../../etc/passwd", "캡션", futureIso())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-01: 캡션이 2200자를 넘으면 422")
    void createRejectsTooLongCaption() throws Exception {
        postQueue("x.mp4", "가".repeat(2201), futureIso())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  멱등성 (skills/backEnd/idempotency-idempotency.md)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("규칙 2: 같은 키로 같은 내용을 두 번 보내면 첫 응답이 그대로 반환된다")
    void idempotentReplayReturnsFirstResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = queueBody("idem-reel.mp4", "멱등 테스트", futureIso());

        MvcResult first = mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        // 같은 queueId 가 돌아와야 한다 — 다르면 예약이 두 번 만들어진 것이다
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("같은 키로 다른 내용을 보내면 422 로 거부한다 (조용히 첫 응답을 주지 않는다)")
    void idempotentKeyReuseWithDifferentBodyRejected() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueBody("first.mp4", "첫 내용", futureIso())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueBody("second.mp4", "다른 내용", futureIso())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("규칙 3: 멱등성 키가 UUID v4 가 아니면 422 로 거부한다")
    void idempotencyKeyMustBeUuidV4() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueBody("x.mp4", "캡션", futureIso())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("실패한 요청의 멱등성 키는 해제되어 재시도할 수 있다")
    void failedRequestReleasesKey() throws Exception {
        String key = UUID.randomUUID().toString();
        // 경로 탐색으로 일부러 실패시킨다
        String badBody = queueBody("../../escape.mp4", "캡션", futureIso());

        mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isUnprocessableContent());

        // 같은 키로 올바른 요청을 다시 보내면 처리되어야 한다.
        // 해제하지 않으면 이 키는 48시간 동안 409 만 돌려준다.
        mockMvc.perform(post("/api/v1/queues")
                        .cookie(authCookie)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueBody("retry-ok.mp4", "캡션", futureIso())))
                .andExpect(status().isCreated());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API-02 GET /api/v1/queues — 목록 조회
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("API-02: 목록을 items/total 형태로 반환한다")
    void listQueueItems() throws Exception {
        postQueue("listed.mp4", "캡션", futureIso())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/queues").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists())
                // API-02 는 "실패 상태 및 재시도 상태" 조회를 요구한다
                .andExpect(jsonPath("$.items[0].retryCount").exists())
                .andExpect(jsonPath("$.items[0].queueId").exists());
    }

    @Test
    @DisplayName("POL-03: 조회 결과가 0건이어도 200 과 빈 배열을 반환한다")
    void emptyListReturns200WithEmptyArray() throws Exception {
        // 존재할 수 없는 페이지를 요청해 0건 상황을 만든다
        mockMvc.perform(get("/api/v1/queues")
                        .cookie(authCookie)
                        .param("page", "9999")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("API-02: page/limit 제약을 어기면 422 (명세: page>=0, limit>0)")
    void listRejectsInvalidPaging() throws Exception {
        mockMvc.perform(get("/api/v1/queues").cookie(authCookie).param("page", "-1"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/v1/queues").cookie(authCookie).param("limit", "0"))
                .andExpect(status().isUnprocessableContent());
        // 상한을 넘기면 조용히 잘라내지 않고 알려 준다
        mockMvc.perform(get("/api/v1/queues").cookie(authCookie).param("limit", "1000000"))
                .andExpect(status().isUnprocessableContent());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API-03 GET /api/v1/history — 이력 조회
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("API-03: 이력을 history 배열로 반환한다")
    void lookupHistory() throws Exception {
        postDataSeeder.run(new DefaultApplicationArguments());

        mockMvc.perform(get("/api/v1/history").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history[0].recordId").exists())
                // ADR-0004: recorded_at 컬럼이 timestamp 필드로 나가야 한다
                .andExpect(jsonPath("$.history[0].timestamp").exists())
                .andExpect(jsonPath("$.history[0].contentHash").exists());
    }

    @Test
    @DisplayName("POL-03: 이력이 0건인 기간을 조회해도 200 과 빈 배열")
    void emptyHistoryReturns200WithEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/history")
                        .cookie(authCookie)
                        .param("startDate", "1990-01-01")
                        .param("endDate", "1990-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history").isEmpty());
    }

    @Test
    @DisplayName("API-03: 인증 없이 호출하면 401")
    void historyRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("API-03: 날짜 형식이 잘못되면 400")
    void historyRejectsBadDate() throws Exception {
        mockMvc.perform(get("/api/v1/history").cookie(authCookie).param("startDate", "2026/07/01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  API-04 POST /api/v1/reels/upload — 릴스 업로드
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("API-04: 정상 영상 파일이면 201 과 containerId/PROCESSING 을 받는다")
    void uploadReel() throws Exception {
        writeValidMp4("upload-ok.mp4");

        mockMvc.perform(post("/api/v1/reels/upload")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"upload-ok.mp4\",\"caption\":\"릴스 캡션\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.containerId").exists())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("API-04: 파일이 없으면 422 VALIDATION_ERROR")
    void uploadRejectsMissingFile() throws Exception {
        mockMvc.perform(post("/api/v1/reels/upload")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"does-not-exist.mp4\",\"caption\":\"캡션\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-04: 확장자만 바꾼 파일은 422 로 거부한다 (매직 바이트 검증)")
    void uploadRejectsFakeVideo() throws Exception {
        Path fake = pathValidator.getAllowedBaseDir().resolve("fake-video.mp4");
        Files.write(fake, "이건 영상이 아니라 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/v1/reels/upload")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"fake-video.mp4\",\"caption\":\"캡션\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-04: 캡션은 필수다 (명세: caption 필수 O)")
    void uploadRequiresCaption() throws Exception {
        writeValidMp4("needs-caption.mp4");

        mockMvc.perform(post("/api/v1/reels/upload")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"needs-caption.mp4\",\"caption\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-04: 인증 없이 호출하면 401")
    void uploadRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/reels/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"x.mp4\",\"caption\":\"캡션\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API-04: 접수된 업로드는 목록에서 같은 id 로 추적할 수 있다")
    void uploadedJobAppearsInQueueList() throws Exception {
        writeValidMp4("trackable.mp4");

        MvcResult result = mockMvc.perform(post("/api/v1/reels/upload")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binaryPath\":\"trackable.mp4\",\"caption\":\"추적 테스트\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String containerId = result.getResponse().getContentAsString()
                .replaceAll(".*\"containerId\":\"([^\"]+)\".*", "$1");

        // ADR-0012: containerId 는 QueueItem 의 id 이므로 목록에서 조회된다
        assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(UUID.fromString(containerId)))
                .isPresent();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  도우미
    // ═══════════════════════════════════════════════════════════════════

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(adminUsername, adminPassword)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(JwtProperties.ACCESS_COOKIE);
        assertThat(cookie).as("로그인 쿠키가 있어야 한다").isNotNull();
        return cookie;
    }

    private ResultActions postQueue(String mediaPath, String caption, String scheduledAt)
            throws Exception {
        return mockMvc.perform(post("/api/v1/queues")
                .cookie(authCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(queueBody(mediaPath, caption, scheduledAt)));
    }

    private static String queueBody(String mediaPath, String caption, String scheduledAt) {
        return "{\"mediaPath\":\"%s\",\"caption\":\"%s\",\"scheduledAt\":\"%s\"}"
                .formatted(mediaPath, caption, scheduledAt);
    }

    private static String futureIso() {
        return Instant.now().plus(3, ChronoUnit.DAYS).toString();
    }

    /** 오프셋 4~8 이 'ftyp' 인 최소 MP4 파일을 허용 디렉터리에 만든다. */
    private void writeValidMp4(String name) throws IOException {
        byte[] content = new byte[2048];
        content[3] = 0x20;
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, content, 4, 4);
        System.arraycopy("isom".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);
        Files.write(pathValidator.getAllowedBaseDir().resolve(name), content);
    }
}
