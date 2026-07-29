package com.autoinstagram.backend.security.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-03 {@code TokenRefreshed} — 인스타그램 액세스 토큰이 자동으로 갱신됨.
 *
 * <p>2_ddd.md §2 CTX-02 Domain Events 의 payload 를 그대로 옮겼다:
 * <table>
 *   <tr><th>필드</th><th>타입</th><th>필수</th><th>설명</th></tr>
 *   <tr><td>credentialId</td><td>uuid</td><td>true</td><td>자격 증명 식별자</td></tr>
 *   <tr><td>expiresAt</td><td>datetime</td><td>true</td><td>만료 시각 (UTC)</td></tr>
 *   <tr><td>refreshedAt</td><td>datetime</td><td>true</td><td>갱신 시각 (UTC)</td></tr>
 * </table>
 *
 * <p>발행 Aggregate: {@link com.autoinstagram.backend.security.domain.SecurityCredential} (AGG-03)
 *
 * <p><b>토큰 값이 payload 에 없는 것은 의도된 설계다</b> — POL-05(토큰 전문 노출률 0%).
 * 이벤트는 로그·감사·후속 핸들러로 흘러가므로, 토큰을 담으면 노출 경로가 그만큼 늘어난다.
 * 필요한 핸들러는 credentialId 로 조회하면 된다.
 *
 * <p>전달 방식: in-process (3_architecture.md 에 메시지 브로커가 없으므로
 * Spring {@code ApplicationEventPublisher} 를 쓴다 — 2_ddd.md 구현 체크리스트가 지시한 대로
 * Architecture 문서를 근거로 선택).
 */
public record TokenRefreshed(
        UUID credentialId,
        Instant expiresAt,
        Instant refreshedAt
) {

    public TokenRefreshed {
        if (credentialId == null || expiresAt == null || refreshedAt == null) {
            throw new IllegalArgumentException("TokenRefreshed 의 모든 payload 필드는 필수입니다");
        }
    }
}
