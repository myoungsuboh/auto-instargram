package com.autoinstagram.backend.post.domain.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EVT-02 {@code HistoryRecordCreated} 수신 측
 * (2_ddd.md §3 CTX-01 구현 체크리스트 "Event Handler").
 *
 * <p>성공 경로의 감사 기록이다 — POL-01 이 실패 경로를 요구하는 것과 짝을 이룬다.
 * 커밋 이후에만 로그를 남겨, 롤백된 트랜잭션이 "기록됐다"고 남기지 않게 한다.
 */
@Component
public class HistoryRecordCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(HistoryRecordCreatedListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHistoryRecordCreated(HistoryRecordCreated event) {
        // mediaHash 는 콘텐츠 해시일 뿐 비밀이 아니므로 그대로 남겨도 된다(추적에 필요하다)
        log.info("[EVT-02 HistoryRecordCreated] historyId={} mediaHash={} occurredAt={}",
                event.historyId(), event.mediaHash(), event.occurredAt());
    }
}
