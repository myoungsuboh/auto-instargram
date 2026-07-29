package com.autoinstagram.backend.post.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-02 {@code HistoryRecordCreated} — 게시 이력이 성공적으로 기록됨.
 *
 * <p>2_ddd.md §2 CTX-01 Domain Events 의 payload 를 그대로 옮겼다:
 * <table>
 *   <tr><th>필드</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>historyId</td><td>uuid</td><td>true</td><td></td><td>이력 식별자</td></tr>
 *   <tr><td>mediaHash</td><td>string</td><td>true</td><td>length: 64</td><td>미디어 SHA-256 해시값</td></tr>
 *   <tr><td>occurredAt</td><td>datetime</td><td>true</td><td></td><td>기록 시각 (UTC)</td></tr>
 * </table>
 *
 * <p>발행 Aggregate: {@link com.autoinstagram.backend.post.domain.HistoryRecord} (AGG-01)
 * / 트리거 Story: Story-01.4
 */
public record HistoryRecordCreated(
        UUID historyId,
        String mediaHash,
        Instant occurredAt
) {

    /** 2_ddd.md 가 payload 제약으로 명시한 길이. */
    private static final int MEDIA_HASH_LENGTH = 64;

    public HistoryRecordCreated {
        if (historyId == null || mediaHash == null || occurredAt == null) {
            throw new IllegalArgumentException("HistoryRecordCreated 의 모든 payload 필드는 필수입니다");
        }
        if (mediaHash.length() != MEDIA_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "mediaHash 는 " + MEDIA_HASH_LENGTH + "자여야 합니다 (2_ddd.md EVT-02 제약)");
        }
    }
}
