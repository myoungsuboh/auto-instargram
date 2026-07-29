package com.autoinstagram.backend.post.api;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.common.idempotency.IdempotencyService;
import com.autoinstagram.backend.common.util.Sha256;
import com.autoinstagram.backend.post.api.dto.ReelsUploadRequest;
import com.autoinstagram.backend.post.api.dto.ReelsUploadResponse;
import com.autoinstagram.backend.post.service.ReelsUploadService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * API-04 {@code POST /api/v1/reels/upload} — 릴스 업로드 파이프라인 실행 API.
 *
 * <p>1_spack.md:
 * <ul>
 *   <li>구현 Story: Story-06.1 "Resumable 바이너리 직접 업로드 및 4단계 파이프라인"</li>
 *   <li>응답 201 Created: {@code {"containerId": "...", "status": "PROCESSING"}}</li>
 *   <li>에러: 401 AUTH_REQUIRED / 422 VALIDATION_ERROR("바이너리 검증 실패 또는 한도 초과")</li>
 *   <li>required_roles: {@code [system_operator, system_admin]}
 *       — 강제는 {@link com.autoinstagram.backend.config.SecurityConfig}</li>
 * </ul>
 *
 * <p>업로드는 부작용이 있는 POST 이므로 {@code Idempotency-Key} 헤더를 지원한다
 * (skills/backEnd/idempotency-idempotency.md 규칙 1) — 같은 영상이 두 번 게시되는 것을 막는다.
 */
@RestController
@RequestMapping("/api/v1/reels")
public class ReelsController {

    private static final Logger log = LoggerFactory.getLogger(ReelsController.class);

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String METHOD_POST = "POST";
    private static final String PATH = "/api/v1/reels/upload";

    private final ReelsUploadService uploadService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ReelsController(ReelsUploadService uploadService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.uploadService = uploadService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * 릴스 업로드를 접수한다.
     *
     * <p>동기 구간에서 검증·등록만 하고 201 을 즉시 돌려준다(POL-04).
     * 실제 4단계 게시는 {@code PublishWorker} 가 백그라운드에서 수행하며,
     * 진행 상태는 {@code GET /api/v1/queues} 에서 이 {@code containerId} 로 확인할 수 있다.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @Valid @RequestBody ReelsUploadRequest request,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return created(accept(request));
        }

        String fingerprint = Sha256.hex(writeJson(request));

        Optional<IdempotencyService.StoredResponse> replay =
                idempotencyService.beginOrReplay(idempotencyKey, METHOD_POST, PATH, fingerprint);
        if (replay.isPresent()) {
            IdempotencyService.StoredResponse stored = replay.get();
            return ResponseEntity.status(stored.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stored.body());
        }

        ReelsUploadResponse response;
        try {
            response = accept(request);
        } catch (RuntimeException ex) {
            // 실패는 멱등 보호 대상이 아니다 — 표시를 풀어 재시도를 허용한다
            idempotencyService.release(idempotencyKey, METHOD_POST, PATH);
            throw ex;
        }

        idempotencyService.complete(idempotencyKey, METHOD_POST, PATH,
                HttpStatus.CREATED.value(), writeJson(response));

        return created(response);
    }

    private ReelsUploadResponse accept(ReelsUploadRequest request) {
        return ReelsUploadResponse.accepted(
                uploadService.requestUpload(request.binaryPath(), request.caption()));
    }

    private static ResponseEntity<ReelsUploadResponse> created(ReelsUploadResponse body) {
        // 명세가 201 Created 를 규정한다
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            // Jackson 3 의 직렬화 예외는 unchecked 다. 삼키지 않고 500 으로 올린다.
            log.error("응답 직렬화 실패", ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "응답 직렬화 실패", ex);
        }
    }
}
