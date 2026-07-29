package com.autoinstagram.backend.post.api;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.common.idempotency.IdempotencyService;
import com.autoinstagram.backend.common.util.Sha256;
import com.autoinstagram.backend.post.api.dto.QueueCreateRequest;
import com.autoinstagram.backend.post.api.dto.QueueCreateResponse;
import com.autoinstagram.backend.post.api.dto.QueueListResponse;
import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.service.MediaPathValidator;
import com.autoinstagram.backend.post.service.QueueService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * API-01 {@code POST /api/v1/queues} · API-02 {@code GET /api/v1/queues}
 *
 * <p>1_spack.md:
 * <ul>
 *   <li>API-01 — Story-01.1, 201 Created, required_roles {@code [system_admin, system_operator]}</li>
 *   <li>API-02 — Story-01.2, 200 OK, required_roles {@code [system_operator, system_admin]}</li>
 * </ul>
 * 권한 강제는 {@link com.autoinstagram.backend.config.SecurityConfig} 가 경로 단위로 한다
 * (SKL-AUTHN-AUTHZ 규칙 3: 서버 측 검증).
 */
@RestController
@RequestMapping("/api/v1/queues")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String METHOD_POST = "POST";
    private static final String PATH = "/api/v1/queues";

    private final QueueService queueService;
    private final MediaPathValidator mediaPathValidator;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public QueueController(QueueService queueService,
                           MediaPathValidator mediaPathValidator,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.queueService = queueService;
        this.mediaPathValidator = mediaPathValidator;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * API-01 — 예약 큐에 새 게시 작업을 등록한다.
     *
     * <p><b>멱등성</b> (skills/backEnd/idempotency-idempotency.md):
     * 예약 등록은 부작용이 있는 POST 이므로 {@code Idempotency-Key} 헤더를 지원한다(규칙 1).
     * 헤더가 있으면 같은 키의 재요청에 첫 응답을 그대로 돌려주고(규칙 2),
     * 처리 중이면 409 를 반환한다(규칙 4).
     * 헤더가 없으면 멱등 보호 없이 처리한다 — 명세가 헤더를 필수로 규정하지 않았으므로
     * 기존 클라이언트를 깨지 않기 위해 선택 항목으로 둔다.
     */
    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody QueueCreateRequest request,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return created(register(request));
        }

        String fingerprint = fingerprintOf(request);

        // 이미 완료된 요청이면 저장된 응답을 그대로 재생한다 (규칙 2)
        Optional<IdempotencyService.StoredResponse> replay =
                idempotencyService.beginOrReplay(idempotencyKey, METHOD_POST, PATH, fingerprint);
        if (replay.isPresent()) {
            IdempotencyService.StoredResponse stored = replay.get();
            return ResponseEntity.status(stored.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stored.body());
        }

        QueueCreateResponse response;
        try {
            response = register(request);
        } catch (RuntimeException ex) {
            // 실패는 멱등 보호 대상이 아니다 — 표시를 풀어 클라이언트가 재시도할 수 있게 한다.
            // 풀지 않으면 그 키는 TTL(48h) 동안 409 만 돌려준다.
            idempotencyService.release(idempotencyKey, METHOD_POST, PATH);
            throw ex;
        }

        idempotencyService.complete(idempotencyKey, METHOD_POST, PATH,
                HttpStatus.CREATED.value(), writeJson(response));

        return created(response);
    }

    /**
     * API-02 — 예약 큐 목록과 실패·재시도 상태를 조회한다.
     *
     * <p>POL-03: 0건이어도 200 + {@code {"items":[],"total":0}} 을 반환한다.
     */
    @GetMapping
    public QueueListResponse list(@RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer limit) {
        return QueueListResponse.from(queueService.list(page, limit));
    }

    private QueueCreateResponse register(QueueCreateRequest request) {
        // SKL-INPUT-VALIDATION 규칙 6: 경로를 정규화해 허용 디렉터리 안인지 확인한다.
        // 검증된 절대 경로를 저장해, 나중에 파일을 열 때 다시 해석하지 않게 한다.
        String safePath = mediaPathValidator.validate(request.mediaPath()).toString();

        if (safePath.length() > QueueItem.MAX_MEDIA_PATH_LENGTH) {
            // 상대 경로를 절대 경로로 바꾸면서 255자를 넘길 수 있다 — 저장 전에 걸러 낸다
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "정규화된 미디어 경로가 " + QueueItem.MAX_MEDIA_PATH_LENGTH + "자를 초과합니다");
        }

        QueueItem saved = queueService.register(safePath, request.caption(), request.scheduledAt());
        return QueueCreateResponse.from(saved);
    }

    private static ResponseEntity<QueueCreateResponse> created(QueueCreateResponse body) {
        // 명세가 201 Created 를 규정한다
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /** 같은 키로 다른 내용을 보내는 것을 감지하기 위한 요청 본문 지문. */
    private String fingerprintOf(QueueCreateRequest request) {
        return Sha256.hex(writeJson(request));
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
