package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * API-01 {@code POST /api/v1/queues} 응답 본문. **Status**: 201 Created
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>queueId</td><td>uuid</td><td>O</td><td></td><td>생성된 예약 식별자</td></tr>
 *   <tr><td>status</td><td>enum</td><td>O</td><td>PENDING|SUCCESS|FAILED</td><td>큐 상태</td></tr>
 *   <tr><td>createdAt</td><td>datetime</td><td>O</td><td></td><td>등록 시각 (UTC)</td></tr>
 * </table>
 *
 * <p><b>두 가지 변환이 여기서 일어난다</b> (엔티티를 그대로 반환하면 둘 다 깨진다):
 * <ul>
 *   <li>ADR-0004 — DB 컬럼 {@code id} → 응답 필드 {@code queueId}</li>
 *   <li>ADR-0003 — 내부 상태(4가지) → 응답 enum(3가지)</li>
 * </ul>
 */
public record QueueCreateResponse(
        UUID queueId,
        QueueStatus.ApiQueueStatus status,
        Instant createdAt
) {

    public static QueueCreateResponse from(QueueItem item) {
        return new QueueCreateResponse(
                item.getId(),                       // ADR-0004: id → queueId
                item.getStatus().toApiStatus(),     // ADR-0003: 내부 상태 → API enum
                item.getCreatedAt());
    }
}
