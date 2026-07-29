package com.autoinstagram.backend.post.domain.event;

import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import com.autoinstagram.backend.post.service.HistoryService;
import com.autoinstagram.backend.post.service.MediaHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EVT-01 {@code QueueItemFailed} 수신 측
 * (2_ddd.md §3 CTX-01 구현 체크리스트 "Event Handler").
 *
 * <p><b>POL-01 이행 지점</b>: "모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함".
 * 예약 큐 실패가 이력에 남는 것을 이 핸들러가 보장한다.
 *
 * <p>{@code AFTER_COMMIT} 을 쓰는 이유: 큐 항목의 FAILED 상태가 실제로 커밋된 뒤에만 이력을 남긴다.
 * 트랜잭션 중에 기록하면 나중에 롤백될 때 "실패했다는 이력"만 남는 불일치가 생긴다.
 *
 * <p>{@code REQUIRES_NEW} 를 쓰는 이유: 커밋 이후에는 원래 트랜잭션이 이미 끝났으므로
 * 이력 저장을 위한 새 트랜잭션이 필요하다.
 */
@Component
public class QueueItemFailedListener {

    private static final Logger log = LoggerFactory.getLogger(QueueItemFailedListener.class);

    private final QueueItemRepository queueItemRepository;
    private final HistoryService historyService;
    private final MediaHasher mediaHasher;

    public QueueItemFailedListener(QueueItemRepository queueItemRepository,
                                   HistoryService historyService,
                                   MediaHasher mediaHasher) {
        this.queueItemRepository = queueItemRepository;
        this.historyService = historyService;
        this.mediaHasher = mediaHasher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQueueItemFailed(QueueItemFailed event) {
        log.warn("[EVT-01 QueueItemFailed] queueItemId={} errorCode={} failedAt={}",
                event.queueItemId(), event.errorCode(), event.failedAt());

        QueueItem item = queueItemRepository.findByIdAndDeletedAtIsNull(event.queueItemId())
                .orElse(null);

        if (item == null) {
            // 이력을 남길 근거(미디어 경로)를 얻을 수 없다. 삼키지 않고 명시적으로 남긴다.
            log.error("[POL-01] 큐 항목 {} 을 찾을 수 없어 실패 이력을 남기지 못했습니다",
                    event.queueItemId());
            return;
        }

        // 재시도 예정 여부: 아직 재시도 여력이 있으면 RETRY 로, 아니면 FAILED 로 기록한다
        boolean retryPlanned = item.getRetryCount() < MAX_RETRY_BEFORE_GIVING_UP;

        historyService.recordFailure(
                mediaHasher.hashMedia(item.getMediaPath()),
                item.getId(),
                event.errorCode(),
                "예약 발행 실패 (재시도 " + item.getRetryCount() + "회)",
                retryPlanned,
                item.getRetryCount());
    }

    /**
     * 이 횟수를 넘기면 더 이상 재시도하지 않는 것으로 보고 이력을 FAILED 로 확정한다.
     *
     * <p>명세에 재시도 상한이 없어 값을 정해야 했다. 3회는 일시적 오류를 흡수하되
     * 영구 오류에 무한히 매달리지 않는 통상적인 선택이다
     * (공통 규칙: 재시도에는 항상 횟수 상한을 둔다).
     */
    static final int MAX_RETRY_BEFORE_GIVING_UP = 3;
}
