package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.HistoryRecord;
import java.util.List;

/**
 * API-03 {@code GET /api/v1/history} 응답 본문. **Status**: 200 OK
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>history</td><td>array</td><td>O</td><td>item: object</td><td>이력 목록</td></tr>
 * </table>
 *
 * <p>POL-03: 0건이면 {@code {"history": []}} 를 200 으로 반환한다 (명세의 응답 예시와 동일).
 */
public record HistoryListResponse(
        List<HistoryRecordView> history
) {

    public static HistoryListResponse from(List<HistoryRecord> records) {
        return new HistoryListResponse(records.stream().map(HistoryRecordView::from).toList());
    }
}
