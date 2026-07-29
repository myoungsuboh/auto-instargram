package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.util.TokenMasker;
import com.autoinstagram.backend.post.domain.QueueItem;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 발행 실행기 — Story-01.2 "예약 실행 및 실패 정책(failed 마킹 및 재시도 상태 조회)".
 *
 * <p>발행 시각이 된 {@link QueueItem} 을 집어 4단계 파이프라인을 돌린다.
 * 예약 등록(API-01)과 즉시 업로드(API-04) 모두 이 워커가 실행한다 —
 * 즉시 업로드는 {@code scheduledAt=now} 인 예약일 뿐이다 (ADR-0012).
 *
 * <p><b>워커가 필요한 이유</b>: {@code scheduledAt} 을 저장만 하고 아무도 실행하지 않으면
 * "예약 발행"은 동작하지 않는 죽은 기능이 된다. 또 POL-04(API 응답 3초)를 지키려면
 * 영상 업로드를 요청 스레드에서 수행할 수 없다 (ADR-0013).
 *
 * <p>실패 처리는 {@link QueueService#markFailed} 를 통해서만 한다 —
 * 그 안에서 EVT-01 이 발행되고, 수신 측이 POL-01(실패 이력 기록)을 이행한다.
 * 즉 이 워커가 이력을 직접 쓰지 않는다(책임이 한 곳에 있다).
 */
@Component
public class PublishWorker {

    private static final Logger log = LoggerFactory.getLogger(PublishWorker.class);

    /** 한 주기에 처리할 최대 건수. 한 번에 다 처리하려 하면 한 주기가 끝없이 길어진다. */
    private static final int BATCH_LIMIT = 5;

    /**
     * 이 시간 넘게 RUNNING 이면 처리 중 인스턴스가 죽은 것으로 보고 회수한다.
     *
     * <p>파이프라인 3단계(인코딩 대기)가 최대 60초, 업로드 타임아웃이 5분이므로
     * 정상 처리가 이 값을 넘지 않도록 넉넉히 잡았다. 너무 짧으면 정상 처리 중인 항목을
     * 회수해 이중 게시를 유발한다.
     */
    private static final Duration STALLED_AFTER = Duration.ofMinutes(15);

    private final QueueService queueService;
    private final HistoryService historyService;
    private final MediaHasher mediaHasher;
    private final MediaPathValidator pathValidator;
    private final InstagramReelsPublisher publisher;
    private final boolean workerEnabled;

    public PublishWorker(QueueService queueService,
                         HistoryService historyService,
                         MediaHasher mediaHasher,
                         MediaPathValidator pathValidator,
                         InstagramReelsPublisher publisher,
                         @Value("${app.publish.worker-enabled:true}") boolean workerEnabled) {
        this.queueService = queueService;
        this.historyService = historyService;
        this.mediaHasher = mediaHasher;
        this.pathValidator = pathValidator;
        this.publisher = publisher;
        this.workerEnabled = workerEnabled;
    }

    /**
     * 발행 시각이 지난 예약을 처리한다.
     *
     * <p>{@code fixedDelay} 를 쓰는 이유: 이전 주기가 끝난 뒤에 다음 주기를 시작하므로
     * 처리가 오래 걸릴 때 주기가 겹쳐 같은 항목을 두 번 집는 일이 없다
     * ({@code fixedRate} 는 겹칠 수 있다).
     */
    @Scheduled(fixedDelayString = "${app.publish.worker-interval-ms:10000}",
            initialDelayString = "${app.publish.worker-initial-delay-ms:15000}")
    public void processDueItems() {
        if (!workerEnabled) {
            return;
        }

        // 처리 중 인스턴스가 죽어 멈춘 항목을 먼저 회수한다.
        // 회수하지 않으면 그 예약은 영구히 게시되지 않으면서 API 에는 대기 중으로 보인다.
        try {
            int reclaimed = queueService.reclaimStalledItems(STALLED_AFTER);
            if (reclaimed > 0) {
                log.info("멈춘 예약 {}건을 회수했습니다", reclaimed);
            }
        } catch (RuntimeException ex) {
            // 회수 실패가 이번 주기의 정상 처리를 막지 않게 한다 (규칙 5: 장애 격리)
            log.error("멈춘 예약 회수 중 오류 — 이번 주기는 회수 없이 진행합니다", ex);
        }

        List<QueueItem> due;
        try {
            due = queueService.findDueItems(Instant.now());
        } catch (RuntimeException ex) {
            // 워커가 예외로 죽으면 이후 주기가 아예 돌지 않는다. 삼키지 않고 남기되 계속 살아 있게 한다.
            log.error("예약 대상 조회 실패 — 다음 주기에 다시 시도합니다", ex);
            return;
        }

        if (due.isEmpty()) {
            return;
        }

        log.info("예약 발행 대상 {}건 (이번 주기 최대 {}건 처리)", due.size(), BATCH_LIMIT);

        due.stream().limit(BATCH_LIMIT).forEach(this::processOne);
    }

    /**
     * 한 건을 처리한다.
     *
     * <p>항목별로 예외를 격리한다 — 한 건의 실패가 나머지 처리를 막지 않게 한다
     * (SKL-ERROR-HANDLING-RESILIENCE 규칙 5: 장애를 격리한다).
     */
    private void processOne(QueueItem item) {
        // ── 원자적 선점 ────────────────────────────────────────────────
        // "읽어서 확인 → 저장" 방식은 Replicas 2 환경에서 두 인스턴스가 모두 통과해
        // 같은 영상을 두 번 게시한다. 조건부 UPDATE 의 영향 행 수로 승패를 가린다.
        boolean claimed;
        try {
            claimed = queueService.tryClaimForPublishing(item.getId());
        } catch (RuntimeException ex) {
            log.debug("항목 {} 선점 중 오류로 건너뜁니다: {}", item.getId(), ex.getMessage());
            return;
        }
        if (!claimed) {
            // 다른 인스턴스가 먼저 가져갔다. 경쟁에서 진 것이므로 조용히 넘긴다.
            return;
        }

        String contentHash;
        try {
            contentHash = mediaHasher.hashMedia(item.getMediaPath());
        } catch (RuntimeException ex) {
            failWith(item, "MEDIA_HASH_FAILED", ex);
            return;
        }

        try {
            Path mediaPath = pathValidator.validateExistingFile(item.getMediaPath());

            publisher.publish(mediaPath, item.getCaption());

            queueService.markCompleted(item.getId());
            historyService.recordSuccess(contentHash, item.getId(), item.getRetryCount());
            log.info("예약 발행 성공 — queueId={}", item.getId());

        } catch (ApiException ex) {
            // 명세된 오류 코드가 있는 실패 (구성 누락 422, 외부 실패 502 등)
            failWith(item, ex.getErrorCode().name(), ex);

        } catch (RuntimeException ex) {
            // 예상하지 못한 실패도 반드시 기록한다 (POL-01: 누락 없이)
            failWith(item, "UNEXPECTED_ERROR", ex);
        }
    }

    /**
     * 실패로 표시한다. {@link QueueService#markFailed} 가 EVT-01 을 발행하고,
     * 그 수신 측이 실패 이력을 남긴다(POL-01).
     */
    private void failWith(QueueItem item, String errorCode, Exception cause) {
        // POL-05: 예외 메시지에 토큰이 섞여 있을 수 있다
        String safe = TokenMasker.scrub(cause.getMessage());
        log.warn("예약 발행 실패 — queueId={}, errorCode={}, 원인={}", item.getId(), errorCode, safe);

        try {
            queueService.markFailed(item.getId(), errorCode);
        } catch (RuntimeException ex) {
            // 실패 기록조차 실패하면 그 사실이 사라지지 않게 error 로 남긴다 (POL-01)
            log.error("[POL-01] 항목 {} 의 실패 상태 저장에 실패했습니다", item.getId(), ex);
        }
    }
}
