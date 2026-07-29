package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import com.autoinstagram.backend.post.domain.QueueStatus;
import com.autoinstagram.backend.post.domain.event.QueueItemFailed;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CTX-01 게시 관리 컨텍스트의 <b>Domain Service</b> — 예약 큐 담당
 * (2_ddd.md §3 구현 체크리스트 "Domain Service 클래스").
 *
 * <p>담당 API: API-01 {@code POST /api/v1/queues}, API-02 {@code GET /api/v1/queues}
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    /** API-02 의 limit 이 없을 때 기본값. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * limit 상한. 상한이 없으면 {@code limit=1000000} 같은 요청 하나로
     * DB 와 응답 직렬화가 POL-04(3초)를 넘길 수 있다.
     */
    public static final int MAX_PAGE_SIZE = 100;

    private final QueueItemRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public QueueService(QueueItemRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * API-01 의 본체 — 예약을 등록한다.
     *
     * <p>도메인 검증 실패를 422 VALIDATION_ERROR 로 옮긴다 (1_spack.md API-01 의 에러 표).
     * Bean Validation 이 이미 걸러 주지만, 다른 경로(시드 등)에서 들어온 값도 같은 규칙을 받는다.
     */
    @Transactional
    public QueueItem register(String mediaPath, String caption, Instant scheduledAt) {
        QueueItem item;
        try {
            item = QueueItem.schedule(mediaPath, caption, scheduledAt);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
        }
        QueueItem saved = repository.save(item);
        log.info("예약 등록 — queueId={}, scheduledAt={}", saved.getId(), scheduledAt);
        return saved;
    }

    /**
     * API-02 의 본체 — 예약 목록을 조회한다.
     *
     * <p>POL-03: 0건이어도 예외를 던지지 않는다. 빈 {@link Page} 를 그대로 반환하고
     * 컨트롤러가 {@code {"items":[],"total":0}} 으로 내보낸다.
     *
     * @param page  0 이상. null 이면 0
     * @param limit 1 이상 {@value #MAX_PAGE_SIZE} 이하. null 이면 {@value #DEFAULT_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public Page<QueueItem> list(Integer page, Integer limit) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : limit;

        // 1_spack.md API-02 제약: page >= 0, limit > 0
        if (pageNumber < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "page 는 0 이상이어야 합니다");
        }
        if (pageSize <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit 은 1 이상이어야 합니다");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            // 조용히 잘라내지 않고 알려 준다 — 클라이언트가 "다 받았다"고 오해하면 데이터가 누락된다
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "limit 은 " + MAX_PAGE_SIZE + " 이하여야 합니다");
        }

        return repository.findByDeletedAtIsNull(
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public QueueItem requireById(UUID queueId) {
        return repository.findByIdAndDeletedAtIsNull(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "예약을 찾을 수 없음: " + queueId));
    }

    /** 발행 시각이 지난 대기 항목 (예약 실행 대상). */
    @Transactional(readOnly = true)
    public List<QueueItem> findDueItems(Instant now) {
        return repository
                .findByStatusAndScheduledAtLessThanEqualAndDeletedAtIsNullOrderByScheduledAtAsc(
                        QueueStatus.PENDING, now);
    }

    /**
     * 예약을 실패로 표시하고 EVT-01 {@code QueueItemFailed} 를 발행한다.
     *
     * <p>이벤트 수신 측({@code QueueItemFailedListener})이 POL-01(실패 이력 기록)을 이행한다.
     * 즉 실패를 기록하는 책임이 이 메서드에 흩어져 있지 않다.
     */
    @Transactional
    public QueueItem markFailed(UUID queueId, String errorCode) {
        QueueItem item = requireById(queueId);
        try {
            item.markFailed(errorCode);
        } catch (IllegalStateException ex) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, ex.getMessage(), ex);
        }
        QueueItem saved = repository.save(item);

        eventPublisher.publishEvent(new QueueItemFailed(
                saved.getId(), errorCode, saved.getLastFailedAt()));

        return saved;
    }

    /**
     * <b>원자적 선점</b> — 이 항목을 이 인스턴스가 처리하겠다고 표시한다.
     *
     * <p>3_architecture.md 의 {@code Replicas: 2} 환경에서 두 인스턴스가 같은 항목을
     * 동시에 집어 같은 영상을 두 번 게시하는 것을 막는다.
     * 조건부 UPDATE 의 영향 행 수로 승패를 가리므로 락이 필요 없다.
     *
     * @return 선점에 성공했으면 true, 다른 인스턴스가 이미 가져갔으면 false
     */
    @Transactional
    public boolean tryClaimForPublishing(UUID queueId) {
        int claimed = repository.claimForRunning(queueId);
        if (claimed == 0) {
            log.debug("항목 {} 선점 실패 — 다른 인스턴스가 처리 중이거나 상태가 이미 바뀌었습니다", queueId);
            return false;
        }
        return true;
    }

    /**
     * 처리 중 인스턴스가 죽어 RUNNING 으로 멈춘 항목을 대기 상태로 회수한다.
     *
     * <p>회수하지 않으면 그 예약은 영구히 게시되지 않으면서 API 에는 PENDING(ADR-0003 변환)으로
     * 보여, 운영자가 "아직 대기 중"이라고 오해한다.
     *
     * <p>실패로 처리하지 않고 <b>대기로 되돌리는</b> 이유: 인스턴스가 죽은 것은 이 예약의
     * 잘못이 아니므로 다시 시도할 기회를 준다. 재시도 횟수는 늘리지 않는다.
     *
     * @return 회수한 건수
     */
    @Transactional
    public int reclaimStalledItems(Duration stalledAfter) {
        Instant threshold = Instant.now().minus(stalledAfter);
        List<QueueItem> stalled = repository
                .findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(QueueStatus.RUNNING, threshold);

        if (stalled.isEmpty()) {
            return 0;
        }

        for (QueueItem item : stalled) {
            // RUNNING → FAILED → PENDING 으로 도메인 규칙을 지키며 되돌린다.
            // markFailed 는 retryCount 를 올리므로 여기서는 쓰지 않고 전용 메서드를 쓴다.
            item.reclaimFromStalled();
            repository.save(item);
            log.warn("멈춘 예약을 대기 상태로 회수했습니다 — queueId={}, 마지막 갱신={}",
                    item.getId(), item.getUpdatedAt());
        }
        return stalled.size();
    }

    /** 게시 완료 표시. */
    @Transactional
    public QueueItem markCompleted(UUID queueId) {
        QueueItem item = requireById(queueId);
        try {
            item.markCompleted();
        } catch (IllegalStateException ex) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, ex.getMessage(), ex);
        }
        return repository.save(item);
    }
}
