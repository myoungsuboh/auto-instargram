package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.HistoryRecord;
import com.autoinstagram.backend.post.domain.HistoryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * API-03 {@code GET /api/v1/history} 응답의 {@code history} 배열 원소.
 *
 * <p>1_spack.md 는 {@code history} 를 {@code array (item: object)} 로만 규정했으므로
 * 원소 필드는 ENT-01 의 속성을 그대로 노출한다.
 *
 * <p>여기서 일어나는 변환 (ADR-0003 / ADR-0004):
 * <ul>
 *   <li>{@code id} → {@code recordId}</li>
 *   <li>{@code recorded_at} → {@code timestamp} (컬럼명은 SQL 예약어를 피해 recorded_at)</li>
 *   <li>내부 상태 3가지 → 응답 enum 2가지 (RETRY → FAILED)</li>
 * </ul>
 *
 * <p>{@code errorMessage} 를 응답에 포함하지 않는 이유: 저장 시 마스킹을 거치지만
 * 외부 응답 본문 조각이 담길 수 있어, 굳이 API 로 내보내 노출 표면을 넓히지 않는다(OWASP #4).
 * 실패 원인은 {@code errorCode} 로 충분히 구분된다.
 */
public record HistoryRecordView(
        UUID recordId,
        String contentHash,
        HistoryStatus.ApiHistoryStatus status,
        Instant timestamp,
        UUID queueId,
        String errorCode
) {

    public static HistoryRecordView from(HistoryRecord record) {
        return new HistoryRecordView(
                record.getId(),                        // ADR-0004: id → recordId
                record.getContentHash(),
                record.getStatus().toApiStatus(),       // ADR-0003: 내부 상태 → API enum
                record.getRecordedAt(),                 // ADR-0004: recorded_at → timestamp
                record.getQueueItemId(),
                record.getErrorCode());
    }
}
