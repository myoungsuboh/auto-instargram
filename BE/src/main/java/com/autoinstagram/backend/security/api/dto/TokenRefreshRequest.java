package com.autoinstagram.backend.security.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API-05 {@code POST /api/v1/tokens/refresh} 요청 본문.
 *
 * <p>1_spack.md 명세:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>설명</th></tr>
 *   <tr><td>shortLivedToken</td><td>string</td><td>O</td><td>단기 액세스 토큰</td></tr>
 * </table>
 *
 * <p>SKL-INPUT-VALIDATION 규칙 1(강제는 서버 측에서, 실패는 거부): 길이 상한을 둔다 —
 * 상한이 없으면 거대한 문자열로 메모리·외부 호출을 낭비시킬 수 있다.
 */
public record TokenRefreshRequest(

        @NotBlank(message = "단기 토큰을 입력해 주세요")
        @Size(max = 1000, message = "토큰 길이가 허용 범위를 초과했습니다")
        String shortLivedToken
) {

    /**
     * 토큰을 절대 그대로 출력하지 않는다 (POL-05).
     * record 기본 toString 은 모든 필드를 출력하므로 반드시 덮어써야 한다.
     */
    @Override
    public String toString() {
        return "TokenRefreshRequest{shortLivedToken=***}";
    }
}
