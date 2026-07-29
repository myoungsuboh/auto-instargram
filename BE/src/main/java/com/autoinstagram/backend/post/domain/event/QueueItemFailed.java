package com.autoinstagram.backend.post.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-01 {@code QueueItemFailed} — 예약 큐 작업이 실패하여 failed 상태로 마킹됨.
 *
 * <p>2_ddd.md §2 CTX-01 Domain Events 의 payload 를 그대로 옮겼다:
 * <table>
 *   <tr><th>필드</th><th>타입</th><th>필수</th><th>설명</th></tr>
 *   <tr><td>queueItemId</td><td>uuid</td><td>true</td><td>큐 항목 식별자</td></tr>
 *   <tr><td>errorCode</td><td>string</td><td>true</td><td>에러 코드</td></tr>
 *   <tr><td>failedAt</td><td>datetime</td><td>true</td><td>실패 시각 (UTC)</td></tr>
 * </table>
 *
 * <p>발행 Aggregate: {@link com.autoinstagram.backend.post.domain.QueueItem} (AGG-02)
 * / 트리거 Story: Story-01.2
 *
 * <p>이 이벤트의 수신 측이 POL-01("모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함")을
 * 이행한다 — {@link QueueItemFailedListener} 가 실패 이력을 남긴다.
 *
 * <p>전달 방식: in-process (3_architecture.md 에 메시지 브로커가 없으므로
 * Spring {@code ApplicationEventPublisher} 사용).
 */
public record QueueItemFailed(
        UUID queueItemId,
        String errorCode,
        Instant failedAt
) {

    public QueueItemFailed {
        if (queueItemId == null || errorCode == null || errorCode.isBlank() || failedAt == null) {
            throw new IllegalArgumentException("QueueItemFailed 의 모든 payload 필드는 필수입니다");
        }
    }
}
