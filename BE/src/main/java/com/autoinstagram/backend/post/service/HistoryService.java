package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.util.TokenMasker;
import com.autoinstagram.backend.post.domain.HistoryRecord;
import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.HistoryStatus;
import com.autoinstagram.backend.post.domain.PublishAttempt;
import com.autoinstagram.backend.post.domain.PublishAttemptRepository;
import com.autoinstagram.backend.post.domain.event.HistoryRecordCreated;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CTX-01 게시 관리 컨텍스트의 <b>Domain Service</b> — 이력 담당
 * (2_ddd.md §3 구현 체크리스트 "Domain Service 클래스").
 *
 * <p>구현하는 정책:
 * <ul>
 *   <li><b>POL-01</b> — 모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함.
 *       {@link #recordFailure} 가 단일 창구다. 실패를 기록하는 다른 경로를 만들지 않는다.
 *       <p><b>기록은 두 곳에 남는다</b>:
 *       {@link HistoryRecord} 는 미디어당 1행(AGG-01 불변식 1)이라 같은 영상의 여러 시도가
 *       한 행을 덮어쓴다. 그래서 {@link PublishAttempt} 에 모든 시도를 append 해
 *       "누락 없이"를 실제로 달성한다. Phase 3 검증에서 실패 5건 중 3건이 소실되는 것을
 *       관측해 추가했다 (V4 마이그레이션 주석 참조).</li>
 *   <li><b>POL-02</b> — 이력 쓰기는 원자적이어야 함. 파일(history.json) 이 아니라
 *       DB 트랜잭션으로 보장한다 — 파일 기반의 동시성 충돌·손상 위험이 RDBMS 이관의 이유였다.</li>
 *   <li><b>POL-03</b> — 조회 결과 0건이면 빈 목록을 정상 반환한다(예외를 던지지 않는다).</li>
 *   <li><b>POL-05</b> — 저장 전 {@link TokenMasker#scrub} 로 에러 메시지에서 토큰을 제거한다.</li>
 * </ul>
 */
@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    /** API-03 의 startDate 가 없을 때 적용할 기본 조회 범위. */
    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    /** error_message 저장 상한. 외부 응답 전문이 그대로 들어와 테이블이 비대해지는 것을 막는다. */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final HistoryRecordRepository repository;
    private final PublishAttemptRepository attemptRepository;
    private final ApplicationEventPublisher eventPublisher;

    public HistoryService(HistoryRecordRepository repository,
                          PublishAttemptRepository attemptRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * API-03 {@code GET /api/v1/history} 의 본체.
     *
     * <p>POL-03: 결과가 0건이어도 빈 목록을 반환한다. 컨트롤러가 이를 200 + {@code {"history":[]}} 로 내보낸다.
     *
     * @param startDate 없으면 {@value #DEFAULT_LOOKBACK_DAYS}일 전부터
     * @param endDate   없으면 현재까지. 종료일은 그 날 <b>전체</b>를 포함한다(23:59:59.999 까지)
     */
    @Transactional(readOnly = true)
    public List<HistoryRecord> findHistory(LocalDate startDate, LocalDate endDate) {
        // 종료 시각을 먼저 정한다. endDate 를 그대로 자정으로 바꾸면 그 날 하루가 빠지므로
        // 다음 날 자정 직전까지로 잡는다 — 사용자는 "5일까지"라면 5일을 포함할 것으로 기대한다.
        Instant to = endDate != null
                ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1)
                : Instant.now();

        // 기본 조회 범위는 **endDate 기준**으로 거슬러 올라간다.
        // 현재 시각 기준으로 고정하면, endDate 만 지정한 요청에서 그 값이 90일보다 과거일 때
        // from > to 가 되어 데이터가 있어도 무조건 빈 배열이 나갔다.
        // 두 파라미터는 명세상 서로 독립적인 선택 항목이므로 조합에 따라 결과가 달라져선 안 된다.
        Instant from = startDate != null
                ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : to.minusSeconds(DEFAULT_LOOKBACK_DAYS * 86400L);

        if (from.isAfter(to)) {
            // 사용자가 startDate > endDate 로 명시한 경우만 여기에 온다.
            // 빈 결과가 맞다 (예외로 만들면 POL-03 의 취지에 어긋난다).
            log.warn("조회 범위가 뒤집혀 있어 빈 목록을 반환합니다 (from={}, to={})", from, to);
            return List.of();
        }
        return repository.findByRecordedAtBetweenAndDeletedAtIsNullOrderByRecordedAtDesc(from, to);
    }

    /**
     * 게시 성공 이력을 남기고 EVT-02 {@code HistoryRecordCreated} 를 발행한다.
     *
     * <p>같은 미디어의 이력이 이미 있으면(재시도 끝에 성공한 경우) 새 행을 넣지 않고 갱신한다 —
     * AGG-01 불변식 1(해시는 미디어별 유일) 때문이다.
     */
    @Transactional
    public HistoryRecord recordSuccess(String contentHash, UUID queueItemId, int attemptNumber) {
        // POL-01 감사 추적: 시도를 먼저 append 한다. 아래 upsert 가 이전 결과를 덮어써도
        // 이 기록은 남는다 (서로 다른 예약이 같은 영상을 가리킬 때 이력이 뭉개지는 문제 해결)
        attemptRepository.save(PublishAttempt.succeeded(queueItemId, contentHash, attemptNumber));

        Optional<HistoryRecord> existing = repository.findByContentHashAndDeletedAtIsNull(contentHash);

        HistoryRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.updateOutcome(HistoryStatus.SUCCESS, null, null);
            if (queueItemId != null) {
                record.linkQueueItem(queueItemId);
            }
            log.info("게시 이력 갱신(성공) — historyId={}, queueItemId={}", record.getId(), queueItemId);
        } else {
            record = repository.save(HistoryRecord.success(contentHash, queueItemId));
            log.info("게시 이력 생성(성공) — historyId={}, queueItemId={}", record.getId(), queueItemId);
        }

        // EVT-02 발행. payload 는 2_ddd.md 명세 그대로 (historyId / mediaHash / occurredAt)
        eventPublisher.publishEvent(new HistoryRecordCreated(
                record.getId(), record.getContentHash(), record.getRecordedAt()));

        return record;
    }

    /**
     * <b>POL-01 구현</b> — 실패를 이력에 기록한다. 실패 경로는 모두 이 메서드를 거쳐야 한다.
     *
     * @param retryPlanned 재시도 예정이면 내부 상태를 RETRY 로 둔다 (API 응답은 FAILED 로 보인다)
     */
    @Transactional
    public HistoryRecord recordFailure(String contentHash, UUID queueItemId,
                                       String errorCode, String rawErrorMessage,
                                       boolean retryPlanned, int attemptNumber) {
        // POL-05: 외부 응답·예외 메시지에 토큰이 섞여 들어올 수 있으므로 저장 전에 제거한다
        String safeMessage = truncate(TokenMasker.scrub(rawErrorMessage));
        HistoryStatus status = retryPlanned ? HistoryStatus.RETRY : HistoryStatus.FAILED;

        // POL-01 감사 추적: 모든 실패를 하나도 빠뜨리지 않고 append 한다.
        // 아래 upsert 는 미디어당 1행만 유지하므로 이 기록 없이는 실패가 소실된다.
        attemptRepository.save(PublishAttempt.failed(
                queueItemId, contentHash, errorCode, safeMessage, attemptNumber, retryPlanned));

        Optional<HistoryRecord> existing = repository.findByContentHashAndDeletedAtIsNull(contentHash);

        HistoryRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            if (record.isPublished()) {
                // 이미 게시된 미디어다. 이력의 SUCCESS 를 지우지 않는다 —
                // 실패 사실은 위에서 PublishAttempt 에 이미 append 했으므로 정보는 보존된다.
                log.warn("[POL-01] 이미 게시된 미디어의 실패 시도 — 이력은 SUCCESS 로 유지하고 "
                                + "시도 기록만 남깁니다 (historyId={}, queueItemId={}, errorCode={})",
                        record.getId(), queueItemId, errorCode);
                return record;
            }
            record.updateOutcome(status, errorCode, safeMessage);
            if (queueItemId != null) {
                record.linkQueueItem(queueItemId);
            }
        } else {
            record = repository.save(retryPlanned
                    ? HistoryRecord.retrying(contentHash, queueItemId, errorCode, safeMessage)
                    : HistoryRecord.failure(contentHash, queueItemId, errorCode, safeMessage));
        }

        // POL-01 은 "로그 및 이력" 둘 다 요구한다 — 이력 저장과 로그를 같은 자리에서 함께 한다
        log.warn("[POL-01] 실패 이력 기록 — historyId={}, queueItemId={}, status={}, errorCode={}",
                record.getId(), queueItemId, status, errorCode);

        return record;
    }

    /** AGG-01 불변식 1 사전 검사 (친절한 에러용. 최종 방어선은 DB 유니크 인덱스다). */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String contentHash) {
        return repository.existsByContentHashAndDeletedAtIsNull(contentHash);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "…(생략)";
    }
}
