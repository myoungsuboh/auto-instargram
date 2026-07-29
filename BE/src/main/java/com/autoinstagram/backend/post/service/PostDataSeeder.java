package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.util.Sha256;
import com.autoinstagram.backend.post.domain.HistoryRecord;
import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 큐·이력 예시 데이터 시드.
 *
 * <p>00-ORCHESTRATOR Verify 3 요구사항: "seed test queue items and history records <b>idempotently</b>".
 *
 * <p><b>멱등성 처리 방식</b> — 항목별로 존재 여부를 확인해 없는 것만 만든다.
 * ORCHESTRATOR 의 "skip a table that already has rows — NOT the whole database" 지시에 따라
 * "테이블에 행이 하나라도 있으면 전체 건너뛰기"로 만들지 않았다.
 * 그렇게 하면 나중에 시드 항목을 추가해도 영원히 생성되지 않는다.
 *
 * <p>시드 데이터는 <b>목(mock)이 아니다</b> — DB 에 실제로 들어가는 행이며,
 * 화면은 이것을 실제 API 로 조회한다. 운영에서는 {@code SEED_ENABLED=false} 로 끈다.
 *
 * <p>{@link Order} 로 계정 시드보다 뒤에 돌게 한다 — 감사 컬럼 작성자 기록 순서를 예측 가능하게 둔다.
 */
@Component
@Order(20)
public class PostDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostDataSeeder.class);

    /** 시드 데이터를 알아볼 수 있게 하는 경로 접두사. 실제 파일이 없어도 목록 조회에는 문제가 없다. */
    private static final String SEED_PREFIX = "seed-sample-";

    private final QueueItemRepository queueItemRepository;
    private final HistoryRecordRepository historyRepository;
    private final MediaPathValidator pathValidator;
    private final boolean seedEnabled;

    public PostDataSeeder(QueueItemRepository queueItemRepository,
                          HistoryRecordRepository historyRepository,
                          MediaPathValidator pathValidator,
                          @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.queueItemRepository = queueItemRepository;
        this.historyRepository = historyRepository;
        this.pathValidator = pathValidator;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("예약 큐·이력 시드가 비활성화되어 있습니다 (app.seed.enabled=false)");
            return;
        }
        seedQueueItems();
        seedHistoryRecords();
    }

    /**
     * 예시 예약 3건.
     *
     * <p>모두 <b>미래</b> 시각으로 둔다 — 과거로 두면 워커가 즉시 집어 가서 실패 이력을 만들고,
     * 화면에 "대기 중" 예약이 하나도 보이지 않게 된다.
     */
    private void seedQueueItems() {
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS);
        seedQueueItem("reel-morning.mp4", "아침 루틴 릴스 ☀️", base);
        seedQueueItem("reel-lunch.mp4", "점심 메뉴 추천 🍜", base.plus(6, ChronoUnit.HOURS));
        seedQueueItem("reel-evening.mp4", "저녁 산책 브이로그 🌙", base.plus(12, ChronoUnit.HOURS));
    }

    private void seedQueueItem(String fileName, String caption, Instant scheduledAt) {
        // 경로는 컨트롤러가 저장하는 것과 같은 형태(정규화된 절대 경로)로 만든다 —
        // 그렇지 않으면 존재 확인이 매번 실패해 멱등성이 깨진다.
        String mediaPath = pathValidator.getAllowedBaseDir().resolve(SEED_PREFIX + fileName).toString();

        if (queueItemRepository.existsByMediaPathAndDeletedAtIsNull(mediaPath)) {
            log.debug("예약 시드 건너뜀 (이미 있음): {}", fileName);
            return;
        }
        queueItemRepository.save(QueueItem.schedule(mediaPath, caption, scheduledAt));
        log.info("예약 시드 생성: {} ({})", fileName, scheduledAt);
    }

    /** 예시 이력 2건 — 성공 1건, 실패 1건 (화면에서 두 상태를 모두 확인할 수 있게). */
    private void seedHistoryRecords() {
        seedHistory("published-reel-01.mp4", true, null);
        seedHistory("failed-reel-02.mp4", false, "UPSTREAM_UNAVAILABLE");
    }

    private void seedHistory(String fileName, boolean success, String errorCode) {
        // 해시는 파일명에서 결정적으로 만든다 — 매 실행마다 달라지면 멱등성이 깨진다
        String contentHash = Sha256.hex(SEED_PREFIX + fileName);

        if (historyRepository.existsByContentHashAndDeletedAtIsNull(contentHash)) {
            log.debug("이력 시드 건너뜀 (이미 있음): {}", fileName);
            return;
        }

        HistoryRecord record = success
                ? HistoryRecord.success(contentHash, null)
                : HistoryRecord.failure(contentHash, null, errorCode, "예시 실패 이력 (시드 데이터)");

        historyRepository.save(record);
        log.info("이력 시드 생성: {} ({})", fileName, success ? "성공" : "실패");
    }
}
