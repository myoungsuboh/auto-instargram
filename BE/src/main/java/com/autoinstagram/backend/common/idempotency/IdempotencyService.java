package com.autoinstagram.backend.common.idempotency;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 처리 (skills/backEnd/idempotency-idempotency.md).
 *
 * <p>규칙 매핑:
 * <ul>
 *   <li>규칙 1 — 부작용 있는 POST 는 {@code Idempotency-Key} 헤더를 지원한다 (컨트롤러가 헤더를 받아 넘긴다)</li>
 *   <li>규칙 2 — 첫 요청 결과를 저장하고 재요청 시 저장된 결과를 반환한다 ({@link #beginOrReplay})</li>
 *   <li>규칙 3 — 키는 클라이언트가 만든 UUID v4, TTL 24시간 이상 ({@link IdempotencyRecord#TTL} = 48h)</li>
 *   <li>규칙 4 — 처리 중인 요청의 중복 도달은 409 Conflict</li>
 * </ul>
 *
 * <p><b>트랜잭션 경계가 중요하다.</b> {@link #beginOrReplay} 는 {@code REQUIRES_NEW} 로
 * 즉시 커밋한다 — 처리 시작 표시가 커밋되지 않으면 동시에 들어온 두 번째 요청이 그것을 볼 수 없어
 * 중복 감지가 무력화된다.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 처리를 시작하거나, 이미 완료된 요청이면 저장된 응답을 돌려준다.
     *
     * @return 이미 완료된 요청이면 저장된 응답, 처음 보는 요청이면 {@link Optional#empty()}
     *         (호출자가 실제 처리를 진행해야 한다)
     * @throws ApiException 409 처리 중 중복 / 422 같은 키에 다른 본문
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StoredResponse> beginOrReplay(String key, String method, String path,
                                                  String fingerprint) {
        validateKeyFormat(key);

        Optional<IdempotencyRecord> found = repository
                .findByIdempotencyKeyAndRequestMethodAndRequestPathAndDeletedAtIsNull(key, method, path);

        if (found.isPresent()) {
            IdempotencyRecord record = found.get();

            if (record.isExpired()) {
                // TTL 이 지난 키는 새 요청으로 취급한다. 기존 행을 논리 삭제해
                // 유니크 인덱스(활성 레코드 한정)를 비워 주고 새로 시작한다.
                record.softDelete("system");
                repository.save(record);
                repository.flush();
                log.info("만료된 멱등성 키를 새 요청으로 처리합니다 (key={})", key);
                return insertInProgress(key, method, path, fingerprint);
            }

            // 같은 키로 다른 내용을 보낸 경우 — 조용히 첫 응답을 돌려주면 클라이언트가
            // "두 번째 요청도 처리됐다"고 오해한다. 명시적으로 거부한다.
            if (!record.matchesFingerprint(fingerprint)) {
                log.warn("멱등성 키 재사용 감지 — key={}, {} {}", key, method, path);
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "같은 키에 다른 본문: key=" + key);
            }

            if (record.isInProgress()) {
                // 규칙 4
                log.info("처리 중인 요청의 중복 도달 — key={}", key);
                throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS, "처리 중인 키: " + key);
            }

            // 규칙 2: 저장된 결과를 반환
            log.info("멱등성 재요청 — 저장된 응답을 반환합니다 (key={}, status={})",
                    key, record.getResponseStatus());
            return Optional.of(new StoredResponse(record.getResponseStatus(), record.getResponseBody()));
        }

        return insertInProgress(key, method, path, fingerprint);
    }

    private Optional<StoredResponse> insertInProgress(String key, String method, String path,
                                                      String fingerprint) {
        try {
            repository.save(IdempotencyRecord.begin(key, method, path, fingerprint));
            // 동시 요청과 경합하려면 지금 DB 에 반영되어야 한다
            repository.flush();
            return Optional.empty();

        } catch (DataIntegrityViolationException ex) {
            // 유니크 인덱스 충돌 = 바로 그 순간 다른 요청이 같은 키로 먼저 들어왔다.
            // 애플리케이션 락 없이 DB 가 직렬화해 준 결과이므로, 이것이 곧 중복 감지다 (규칙 4).
            log.info("동시 중복 요청 감지 (유니크 제약) — key={}", key);
            throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS,
                    "동시 중복 요청: key=" + key, ex);
        }
    }

    /** 처리 완료를 기록한다. 이후 같은 키의 요청은 이 응답을 그대로 받는다 (규칙 2). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, String method, String path, int status, String body) {
        repository.findByIdempotencyKeyAndRequestMethodAndRequestPathAndDeletedAtIsNull(key, method, path)
                .ifPresentOrElse(
                        record -> {
                            record.complete(status, body);
                            repository.save(record);
                        },
                        // 표시가 사라졌다면 TTL 정리나 수동 삭제가 있었던 것이다.
                        // 업무 처리 자체는 이미 성공했으므로 예외로 뒤집지 않고 기록만 남긴다.
                        () -> log.warn("완료 처리할 멱등성 기록을 찾지 못했습니다 (key={})", key));
    }

    /**
     * 처리가 실패했을 때 시작 표시를 풀어 준다.
     *
     * <p>풀지 않으면 그 키는 TTL(48시간) 동안 계속 409 를 돌려주어, 클라이언트가
     * 정상적인 재시도조차 할 수 없게 된다. 실패는 멱등성 보호 대상이 아니다 —
     * 보호해야 하는 것은 "성공한 부작용이 두 번 일어나는 것"이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key, String method, String path) {
        repository.findByIdempotencyKeyAndRequestMethodAndRequestPathAndDeletedAtIsNull(key, method, path)
                .filter(IdempotencyRecord::isInProgress)
                .ifPresent(record -> {
                    record.softDelete("system");
                    repository.save(record);
                    log.info("실패한 요청의 멱등성 표시를 해제했습니다 (key={})", key);
                });
    }

    /**
     * 규칙 3: 키는 클라이언트가 생성한 UUID v4 여야 한다.
     *
     * <p>형식을 강제하는 이유: 순번("1", "2")이나 사용자 ID 같은 값을 키로 쓰면
     * 서로 다른 클라이언트가 같은 키를 만들어 남의 응답을 받아 갈 수 있다.
     */
    private static void validateKeyFormat(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 가 비어 있음");
        }
        try {
            UUID parsed = UUID.fromString(key);
            if (parsed.version() != 4) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Idempotency-Key 는 UUID v4 여야 합니다 (받은 버전: " + parsed.version() + ")");
            }
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key 가 UUID 형식이 아닙니다", ex);
        }
    }

    /**
     * 저장된 첫 응답.
     *
     * @param status HTTP 상태 코드
     * @param body   JSON 문자열 (그대로 다시 내보낸다)
     */
    public record StoredResponse(Integer status, String body) {
    }
}
