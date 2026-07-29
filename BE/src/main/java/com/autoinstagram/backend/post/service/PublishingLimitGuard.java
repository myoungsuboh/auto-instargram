package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.HistoryStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시 한도 확인 — 1_spack.md API-04 의 PRD 발췌
 * "순수 바이너리 파서 기반 로컬 사전 검증 및 <b>게시 한도 확인</b>".
 *
 * <p>인스타그램 Graph API 는 24시간당 게시 횟수를 제한한다(공개 문서 기준 25건).
 * 한도를 넘겨 호출하면 외부 API 가 거부하는데, 그 시점에는 이미 영상 업로드에
 * 시간과 대역폭을 쓴 뒤다. 그래서 <b>보내기 전에</b> 우리가 먼저 센다.
 *
 * <p>세는 기준은 성공 이력이다 — 실패는 인스타그램의 게시 한도를 소모하지 않는다.
 */
@Component
public class PublishingLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(PublishingLimitGuard.class);

    /** 한도를 세는 시간 창. */
    private static final int WINDOW_HOURS = 24;

    private final HistoryRecordRepository historyRepository;
    private final int maxPublishesPerWindow;

    public PublishingLimitGuard(HistoryRecordRepository historyRepository,
                               @Value("${app.instagram.max-publishes-per-day:25}") int maxPublishesPerWindow) {
        this.historyRepository = historyRepository;
        this.maxPublishesPerWindow = maxPublishesPerWindow;
    }

    /**
     * 지금 게시할 수 있는지 확인한다.
     *
     * @throws ApiException 422 VALIDATION_ERROR — 명세가 "한도 초과"를 이 코드로 규정했다
     */
    @Transactional(readOnly = true)
    public void ensureWithinLimit() {
        int used = countRecentPublishes();
        if (used >= maxPublishesPerWindow) {
            log.warn("게시 한도 초과 — 최근 {}시간 성공 {}건 (한도 {}건)",
                    WINDOW_HOURS, used, maxPublishesPerWindow);
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "게시 한도 초과: 최근 " + WINDOW_HOURS + "시간 " + used
                            + "건 (한도 " + maxPublishesPerWindow + "건)");
        }
        log.debug("게시 한도 확인 — 최근 {}시간 {}/{}건", WINDOW_HOURS, used, maxPublishesPerWindow);
    }

    /** 최근 시간 창 안의 성공 게시 횟수. */
    @Transactional(readOnly = true)
    public int countRecentPublishes() {
        Instant since = Instant.now().minus(WINDOW_HOURS, ChronoUnit.HOURS);
        return (int) historyRepository
                .findByRecordedAtBetweenAndDeletedAtIsNullOrderByRecordedAtDesc(since, Instant.now())
                .stream()
                // 실패·재시도는 인스타그램의 게시 한도를 쓰지 않는다
                .filter(record -> record.getStatus() == HistoryStatus.SUCCESS)
                .count();
    }

    public int getMaxPublishesPerWindow() {
        return maxPublishesPerWindow;
    }
}
