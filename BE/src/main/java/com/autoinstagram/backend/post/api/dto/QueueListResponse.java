package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * API-02 {@code GET /api/v1/queues} 응답 본문. **Status**: 200 OK
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>items</td><td>array</td><td>O</td><td>item: object</td><td>예약 큐 목록</td></tr>
 *   <tr><td>total</td><td>integer</td><td>O</td><td>&gt;=0</td><td>전체 항목 수</td></tr>
 * </table>
 *
 * <p>POL-03: 결과가 0건이면 {@code {"items": [], "total": 0}} 을 200 으로 반환한다 —
 * 404 나 빈 응답이 아니다. 명세의 응답 예시도 {@code {"items": [], "total": 0}} 이다.
 */
public record QueueListResponse(
        List<QueueItemView> items,
        long total
) {

    public static QueueListResponse from(Page<QueueItem> page) {
        return new QueueListResponse(
                page.getContent().stream().map(QueueItemView::from).toList(),
                // total 은 현재 페이지 크기가 아니라 전체 개수다 (명세: "전체 항목 수")
                page.getTotalElements());
    }
}
