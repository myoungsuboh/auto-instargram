package com.autoinstagram.backend.security.api.dto;

/**
 * API-05 {@code POST /api/v1/tokens/refresh} 응답 본문. **Status**: 200 OK
 *
 * <p>1_spack.md 명세를 그대로 따른다:
 * <table>
 *   <tr><th>이름</th><th>타입</th><th>필수</th><th>제약</th><th>설명</th></tr>
 *   <tr><td>accessToken</td><td>string</td><td>O</td><td></td><td>갱신된 장기 토큰</td></tr>
 *   <tr><td>expiresIn</td><td>integer</td><td>O</td><td>&gt;0</td><td>만료 시간 (초)</td></tr>
 * </table>
 *
 * <p>⚠️ 이 응답에는 토큰 전문이 들어간다 — <b>명세가 그렇게 규정했기 때문이다</b>
 * (응답 예시: {@code {"accessToken": "EAAG...", "expiresIn": 5184000}}).
 * POL-05 는 "운영 로그 및 에러 메시지"에서의 노출을 0% 로 요구하므로 이 응답 자체는 위반이 아니지만,
 * 이 값이 로그로 흘러가지 않도록 {@link #toString()} 을 반드시 덮어쓴다.
 * 이 엔드포인트는 system_admin 만 호출할 수 있다(SecurityConfig).
 */
public record TokenRefreshResponse(
        String accessToken,
        long expiresIn
) {

    @Override
    public String toString() {
        return "TokenRefreshResponse{expiresIn=" + expiresIn + ", accessToken=***}";
    }
}
