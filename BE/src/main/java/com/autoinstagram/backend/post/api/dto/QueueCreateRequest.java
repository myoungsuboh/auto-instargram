package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * API-01 {@code POST /api/v1/queues} 요청 본문.
 *
 * <p>1_spack.md 명세를 그대로 옮겼다:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>mediaPath</td><td>string</td><td>O</td><td>len&lt;=255</td><td>업로드할 미디어 파일 경로</td></tr>
 *   <tr><td>caption</td><td>string</td><td>-</td><td>len&lt;=2200</td><td>게시물 캡션</td></tr>
 *   <tr><td>scheduledAt</td><td>datetime</td><td>O</td><td></td><td>예약 발행 시각 (UTC)</td></tr>
 * </table>
 *
 * <p>SKL-INPUT-VALIDATION 규칙 1(강제는 서버 측에서, 실패는 거부)에 따라
 * 모든 제약을 선언적 스키마로 표현한다. 경로 문자열의 안전성 검사(Path Traversal)는
 * 선언만으로 표현할 수 없으므로 {@code MediaPathValidator} 가 별도로 담당한다(규칙 6).
 */
public record QueueCreateRequest(

        @NotBlank(message = "미디어 파일 경로를 입력해 주세요")
        @Size(max = QueueItem.MAX_MEDIA_PATH_LENGTH,
                message = "미디어 경로는 " + QueueItem.MAX_MEDIA_PATH_LENGTH + "자를 넘을 수 없습니다")
        String mediaPath,

        @Size(max = QueueItem.MAX_CAPTION_LENGTH,
                message = "캡션은 " + QueueItem.MAX_CAPTION_LENGTH + "자를 넘을 수 없습니다")
        String caption,

        @NotNull(message = "예약 발행 시각을 입력해 주세요")
        Instant scheduledAt
) {
}
