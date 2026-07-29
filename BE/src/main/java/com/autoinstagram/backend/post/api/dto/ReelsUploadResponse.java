package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import java.util.UUID;

/**
 * API-04 {@code POST /api/v1/reels/upload} 응답 본문. **Status**: 201 Created
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>설명</th></tr>
 *   <tr><td>containerId</td><td>uuid</td><td>O</td><td>생성된 컨테이너 식별자</td></tr>
 *   <tr><td>status</td><td>string</td><td>O</td><td>업로드 진행 상태</td></tr>
 * </table>
 * 응답 예시: {@code {"containerId": "...", "status": "PROCESSING"}}
 *
 * <p><b>{@code containerId} 는 우리 쪽 식별자다</b> — 명세가 타입을 {@code uuid} 로 규정했고
 * 인스타그램의 컨테이너 ID 는 숫자 문자열이므로 그것을 그대로 쓸 수 없다.
 * 이 값은 이 업로드 작업을 나타내는 {@link QueueItem} 의 id 다
 * (별도 테이블을 만들지 않고 기존 애그리거트를 재사용한 이유는 ADR-0012 참조).
 * 즉 이 id 로 {@code GET /api/v1/queues} 목록에서 진행 상태를 추적할 수 있다.
 */
public record ReelsUploadResponse(
        UUID containerId,
        String status
) {

    /**
     * 명세의 {@code status} 는 enum 이 아니라 자유 형식 string 이고 예시가 {@code "PROCESSING"} 이다.
     * 큐 상태(PENDING/RUNNING)를 그대로 노출하지 않고 이 엔드포인트의 의미대로 변환한다.
     */
    private static final String PROCESSING = "PROCESSING";

    public static ReelsUploadResponse accepted(QueueItem item) {
        return new ReelsUploadResponse(item.getId(), PROCESSING);
    }
}
