package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * API-02 {@code GET /api/v1/queues} 응답의 {@code items} 배열 원소.
 *
 * <p>1_spack.md 는 {@code items} 를 {@code array (item: object)} 로만 규정하고
 * 원소의 필드를 명시하지 않았다. 그래서 화면(SCREEN-01/02)이 실제로 필요한 것을 기준으로 구성했다:
 * <ul>
 *   <li>SCREEN-01 자동 업로드 대시보드 — 예약 목록 표시</li>
 *   <li>SCREEN-02 자동 게시 관리 대시보드 — "실패 상태 및 재시도 상태 조회"(API-02 설명)</li>
 * </ul>
 *
 * <p>{@code retryCount}·{@code lastErrorCode} 를 포함하는 이유: ADR-0003 의 변환 때문에
 * {@code status} 만으로는 재시도 상황을 알 수 없다(RETRY 가 FAILED 로 보인다).
 * API-02 가 요구하는 "재시도 상태 조회"는 이 필드들이 충족한다.
 */
public record QueueItemView(
        UUID queueId,
        String mediaPath,
        String caption,
        Instant scheduledAt,
        QueueStatus.ApiQueueStatus status,
        int retryCount,
        String lastErrorCode,
        Instant lastFailedAt,
        Instant createdAt
) {

    public static QueueItemView from(QueueItem item) {
        return new QueueItemView(
                item.getId(),                       // ADR-0004: id → queueId
                item.getMediaPath(),
                item.getCaption(),
                item.getScheduledAt(),
                item.getStatus().toApiStatus(),     // ADR-0003: 내부 상태 → API enum
                item.getRetryCount(),
                item.getLastErrorCode(),
                item.getLastFailedAt(),
                item.getCreatedAt());
    }
}
