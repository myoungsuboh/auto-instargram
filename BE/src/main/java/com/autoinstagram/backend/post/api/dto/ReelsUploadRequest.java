package com.autoinstagram.backend.post.api.dto;

import com.autoinstagram.backend.post.domain.QueueItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API-04 {@code POST /api/v1/reels/upload} 요청 본문.
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>binaryPath</td><td>string</td><td>O</td><td></td><td>로컬 바이너리 파일 경로</td></tr>
 *   <tr><td>caption</td><td>string</td><td>O</td><td>len&lt;=2200</td><td>릴스 캡션</td></tr>
 * </table>
 *
 * <p>API-01 과 달리 {@code caption} 이 <b>필수</b>다 (명세 그대로).
 *
 * <p>경로 안전성 검사는 선언적 제약으로 표현할 수 없으므로
 * {@link com.autoinstagram.backend.post.service.MediaPathValidator} 가 담당한다
 * (SKL-INPUT-VALIDATION 규칙 6).
 */
public record ReelsUploadRequest(

        @NotBlank(message = "업로드할 파일 경로를 입력해 주세요")
        @Size(max = QueueItem.MAX_MEDIA_PATH_LENGTH,
                message = "파일 경로는 " + QueueItem.MAX_MEDIA_PATH_LENGTH + "자를 넘을 수 없습니다")
        String binaryPath,

        @NotBlank(message = "릴스 캡션을 입력해 주세요")
        @Size(max = QueueItem.MAX_CAPTION_LENGTH,
                message = "캡션은 " + QueueItem.MAX_CAPTION_LENGTH + "자를 넘을 수 없습니다")
        String caption
) {
}
