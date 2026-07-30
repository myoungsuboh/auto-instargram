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
 *
 * <p>{@code igUsername} 은 명세에 없는 <b>추가</b> 필드다(ADR-0024). 기존 필드를 바꾸지 않고
 * 더하기만 하므로 명세를 따르는 소비자는 영향을 받지 않는다. 넣은 이유: 토큰을 갱신했을 때
 * <b>어느 계정에 연결됐는지</b> 화면에서 확인할 수 있어야 한다. 계정을 잘못 연결하고
 * 모른 채 남의 계정에 게시하려 시도하는 것이 가장 위험한 실수다.
 */
public record TokenRefreshResponse(
        String accessToken,
        long expiresIn,
        String igUsername
) {

    @Override
    public String toString() {
        return "TokenRefreshResponse{expiresIn=" + expiresIn
                + ", igUsername=" + igUsername + ", accessToken=***}";
    }
}
