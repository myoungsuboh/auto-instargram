package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.common.util.TokenMasker;
import com.autoinstagram.backend.security.service.SecurityCredentialService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 릴스 게시 4단계 파이프라인 — 1_spack.md API-04 Story-06.1
 * "Resumable 바이너리 직접 업로드 및 <b>4단계 파이프라인</b> 처리".
 *
 * <p>Meta Instagram Graph API 의 공개 문서에 기술된 릴스 게시 흐름을 그대로 4단계로 옮겼다:
 * <ol>
 *   <li><b>컨테이너 생성</b> — {@code POST /{ig-user-id}/media} (media_type=REELS, upload_type=resumable)</li>
 *   <li><b>바이너리 업로드</b> — 1단계가 준 업로드 URI 로 영상 전송 (resumable)</li>
 *   <li><b>처리 대기</b> — {@code GET /{container-id}?fields=status_code} 가 FINISHED 가 될 때까지</li>
 *   <li><b>게시</b> — {@code POST /{ig-user-id}/media_publish} (creation_id=컨테이너)</li>
 * </ol>
 *
 * <p>⚠️ <b>검증 범위</b>: 이 클래스의 HTTP 호출은 인스타그램 비즈니스 계정과 유효한 액세스 토큰이
 * 있어야 실제로 검증할 수 있다. 그런 자격 증명이 없는 환경에서는 {@code app.instagram.publish-enabled}
 * 가 false 이고, 그때는 <b>성공을 흉내내지 않고</b> 명확한 오류로 중단한다 —
 * 가짜 성공은 실패보다 나쁘다(이력에 "게시됨"이 남지만 실제로는 아무 일도 일어나지 않는다).
 *
 * <p>적용 규칙:
 * <ul>
 *   <li>SKL-ERROR-HANDLING-RESILIENCE 규칙 2 — 외부 의존 실패는 502 로, 구성 오류는 422 로 구분</li>
 *   <li>ADR-0009 — 게시는 멱등이 아니므로 이 파이프라인 안에서 재시도하지 않는다</li>
 *   <li>POL-05 — 평문 토큰은 지역 변수로만 존재하고, 예외 메시지는 scrub 을 거친다</li>
 * </ul>
 */
@Component
public class InstagramReelsPublisher {

    private static final Logger log = LoggerFactory.getLogger(InstagramReelsPublisher.class);

    /** 3단계에서 인스타그램이 인코딩을 마쳤음을 나타내는 값. */
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_ERROR = "ERROR";

    /** 3단계 폴링 상한. 영상 인코딩은 수십 초가 걸릴 수 있다. */
    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final SecurityCredentialService credentialService;
    private final RestClient graphClient;
    private final boolean publishEnabled;
    private final String igUserId;

    public InstagramReelsPublisher(
            SecurityCredentialService credentialService,
            @Value("${app.instagram.graph-base-url}") String graphBaseUrl,
            @Value("${app.instagram.publish-enabled:false}") boolean publishEnabled,
            @Value("${app.instagram.user-id:}") String igUserId) {
        this.credentialService = credentialService;
        this.publishEnabled = publishEnabled;
        this.igUserId = igUserId;
        this.graphClient = RestClient.builder()
                .baseUrl(graphBaseUrl)
                .requestFactory(uploadTimeoutFactory())
                .build();
    }

    /**
     * 업로드는 큰 파일을 보내므로 토큰 교환({@code InstagramGraphClient})보다 넉넉한 타임아웃을 쓴다.
     * POL-04(응답 3초)는 <b>우리 API</b>의 응답 시간 규정이고, 이 파이프라인은 API 응답 뒤
     * 백그라운드에서 돌기 때문에 그 예산에 묶이지 않는다 (ADR-0013).
     */
    private static SimpleClientHttpRequestFactory uploadTimeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 무한 대기는 워커 스레드를 영구히 점유한다 (공통 규칙: 외부 호출엔 타임아웃 필수)
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(5));
        return factory;
    }

    /**
     * 4단계 파이프라인을 실행한다.
     *
     * @param mediaPath 이미 {@link MediaPathValidator}·{@link BinaryValidator} 를 통과한 경로
     * @param caption   릴스 캡션
     * @throws ApiException 422 구성·자격 증명 없음 / 502 외부 호출 실패
     */
    public PublishResult publish(Path mediaPath, String caption) {
        if (!publishEnabled) {
            throw new ApiException(ErrorCode.UNPROCESSABLE,
                    "인스타그램 실제 게시가 비활성화되어 있습니다 "
                            + "(INSTAGRAM_PUBLISH_ENABLED=false). 검증·저장·이력 기록은 정상 동작합니다.");
        }
        if (igUserId == null || igUserId.isBlank()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE,
                    "INSTAGRAM_USER_ID 가 설정되지 않아 게시할 수 없습니다.");
        }

        // 평문 토큰은 지역 변수로만 존재한다 (POL-05)
        String token = credentialService.findCurrentPlainToken()
                .orElseThrow(() -> new ApiException(ErrorCode.UNPROCESSABLE,
                        "유효한 인스타그램 액세스 토큰이 없습니다. "
                                + "POST /api/v1/tokens/refresh 로 먼저 발급하세요."));

        try {
            ContainerCreated container = createContainer(token, caption);
            log.info("[1/4] 컨테이너 생성 — id={}", container.id());

            uploadBinary(token, container, mediaPath);
            log.info("[2/4] 바이너리 업로드 완료 — {}", mediaPath.getFileName());

            awaitProcessing(token, container.id());
            log.info("[3/4] 인스타그램 인코딩 완료 — id={}", container.id());

            String mediaId = publishContainer(token, container.id());
            log.info("[4/4] 게시 완료 — mediaId={}", mediaId);

            return new PublishResult(container.id(), mediaId);

        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientException | InterruptedException | IOException ex) {
            if (ex instanceof InterruptedException) {
                // 인터럽트 상태를 삼키지 않는다 — 상위가 종료 신호를 볼 수 있어야 한다
                Thread.currentThread().interrupt();
            }
            // 예외 메시지에 토큰이 담긴 URL 이 섞여 있을 수 있다 (POL-05)
            String safe = TokenMasker.scrub(ex.getMessage());
            log.error("릴스 게시 파이프라인 실패: {}", safe);
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "릴스 게시 실패: " + safe, ex);
        }
    }

    /**
     * 1단계 — 릴스 컨테이너를 만들고 resumable 업로드 URI 를 받는다.
     *
     * <p><b>caption 을 URI 가 아니라 본문(form)으로 보내는 이유</b>: {@code UriBuilder.build(...)} 는
     * 문자열 안의 {@code {...}} 를 URI 템플릿 변수로 해석한다. 사용자가 캡션에 중괄호를 쓰면
     * (예: {@code "오늘 메뉴 {김치찌개} 추천"}) 인스타그램에 요청을 보내기도 전에
     * {@code IllegalArgumentException} 이 나서 그 캡션으로는 게시가 <b>영구히</b> 실패한다.
     * POST 파라미터를 본문으로 보내면 템플릿 해석을 거치지 않아 어떤 문자든 안전하다.
     *
     * <p>토큰도 함께 본문으로 보낸다 — 쿼리스트링에 있으면 프록시·액세스 로그에 남는다(POL-05).
     */
    private ContainerCreated createContainer(String token, String caption) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "REELS");
        form.add("upload_type", "resumable");
        form.add("caption", caption);
        form.add("access_token", token);

        ContainerCreated created = graphClient.post()
                // 경로에는 사용자 입력이 없다. igUserId 는 우리 설정값이다.
                .uri("/{igUserId}/media", igUserId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ContainerCreated.class);

        if (created == null || created.id() == null || created.id().isBlank()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Graph API 가 컨테이너 ID 를 반환하지 않았습니다");
        }
        return created;
    }

    /**
     * 2단계 — 영상 바이너리를 업로드한다.
     *
     * <p>업로드 URI 는 1단계 응답에 담겨 오며, Graph 기본 주소와 호스트가 다르다
     * (rupload.facebook.com). 그래서 절대 URI 로 별도 호출한다.
     */
    private void uploadBinary(String token, ContainerCreated container, Path mediaPath)
            throws IOException {
        long fileSize = Files.size(mediaPath);
        String uploadUri = container.uploadUri();

        if (uploadUri == null || uploadUri.isBlank()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Graph API 가 업로드 URI 를 반환하지 않았습니다");
        }

        RestClient.create()
                .post()
                .uri(uploadUri)
                // 업로드 엔드포인트는 Bearer 가 아니라 OAuth 스킴을 쓴다 (Meta 문서)
                .header("Authorization", "OAuth " + token)
                // offset=0 은 처음부터 보낸다는 뜻. 이어올리기(resume)는 실패 지점의 offset 을 쓴다.
                .header("offset", "0")
                .header("file_size", String.valueOf(fileSize))
                .body(new FileSystemResource(mediaPath))
                .retrieve()
                .toBodilessEntity();
    }

    /** 3단계 — 인코딩이 끝날 때까지 상태를 확인한다. */
    private void awaitProcessing(String token, String containerId) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            // GET 이라 본문을 쓸 수 없으므로 토큰은 Authorization 헤더로 보낸다 —
            // 쿼리스트링에 있으면 프록시·액세스 로그에 그대로 남는다(POL-05).
            // containerId 는 Meta 가 발급한 값이고 사용자 입력이 아니므로 템플릿에 안전하다.
            ContainerStatus status = graphClient.get()
                    .uri(builder -> builder.path("/{containerId}")
                            .queryParam("fields", "status_code,status")
                            .build(containerId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(ContainerStatus.class);

            String code = status == null ? null : status.statusCode();

            if (STATUS_FINISHED.equals(code)) {
                return;
            }
            if (STATUS_ERROR.equals(code)) {
                throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                        "인스타그램이 영상 처리에 실패했습니다: "
                                + TokenMasker.scrub(status.status()));
            }
            log.debug("[3/4] 인코딩 대기 {}/{} — status={}", attempt, MAX_POLL_ATTEMPTS, code);
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        // 무한 대기 금지 (공통 규칙: 재시도·대기에는 상한을 둔다)
        throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                "인스타그램 영상 처리가 제한 시간 내에 끝나지 않았습니다 (컨테이너 " + containerId + ")");
    }

    /** 4단계 — 컨테이너를 발행한다. */
    private String publishContainer(String token, String containerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", containerId);
        form.add("access_token", token);

        PublishedMedia published = graphClient.post()
                .uri("/{igUserId}/media_publish", igUserId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(PublishedMedia.class);

        if (published == null || published.id() == null || published.id().isBlank()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "Graph API 가 게시된 미디어 ID 를 반환하지 않았습니다");
        }
        return published.id();
    }

    /** 실제 게시가 활성화되어 있는지 (호출자가 사전에 판단할 때 쓴다). */
    public boolean isPublishEnabled() {
        return publishEnabled;
    }

    /**
     * 게시 결과.
     *
     * @param instagramContainerId 인스타그램이 발급한 컨테이너 ID (숫자 문자열)
     * @param instagramMediaId     게시된 미디어 ID
     */
    public record PublishResult(String instagramContainerId, String instagramMediaId) {
    }

    // ── Graph API 응답 형태 (snake_case → record 매핑) ────────────────────

    private record ContainerCreated(
            @JsonProperty("id") String id,
            @JsonProperty("uri") String uploadUri
    ) {
    }

    private record ContainerStatus(
            @JsonProperty("status_code") String statusCode,
            @JsonProperty("status") String status
    ) {
    }

    private record PublishedMedia(@JsonProperty("id") String id) {
    }
}
