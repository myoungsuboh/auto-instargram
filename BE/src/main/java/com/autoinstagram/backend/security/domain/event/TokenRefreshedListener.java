package com.autoinstagram.backend.security.domain.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EVT-03 {@code TokenRefreshed} 수신 측
 * (2_ddd.md §3 구현 체크리스트 "Event Handler (이벤트 수신 측이 있는 경우)").
 *
 * <p>{@link TransactionalEventListener} 를 쓰는 이유: 자격 증명 저장 트랜잭션이 <b>커밋된 뒤</b>에만
 * 후속 처리를 한다. 기본 {@code @EventListener} 는 트랜잭션 중에 실행되므로,
 * 나중에 롤백되면 "갱신됐다"는 기록만 남는 불일치가 생긴다.
 *
 * <p>POL-01(모든 실패 경로를 누락 없이 기록)과 짝을 이루는 성공 경로 감사 기록이다.
 * 토큰 값은 payload 에 없으므로 이 로그로 토큰이 샐 수 없다(POL-05).
 */
@Component
public class TokenRefreshedListener {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshedListener.class);

    @TransactionalEventListener
    public void onTokenRefreshed(TokenRefreshed event) {
        log.info("[EVT-03 TokenRefreshed] credentialId={} refreshedAt={} expiresAt={}",
                event.credentialId(), event.refreshedAt(), event.expiresAt());
    }
}
