package com.autoinstagram.backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SKL-AUTHN-AUTHZ 규칙 5 의 <b>IP 별</b> 제한.
 *
 * <p>계정별 제한은 {@link com.autoinstagram.backend.auth.domain.AppAccount} 가 DB 에서 담당한다.
 * 두 축이 모두 필요한 이유:
 * <ul>
 *   <li>계정별만 있으면 — 공격자가 아이디를 바꿔가며 흔한 비밀번호를 계속 시도할 수 있다</li>
 *   <li>IP 별만 있으면 — 분산된 여러 IP 에서 한 계정을 노리는 공격을 못 막는다</li>
 * </ul>
 *
 * <p>메모리에 두는 이유: IP 제한은 짧은 시간 창의 방어라 서버 재시작 시 초기화돼도 무방하고,
 * 매 로그인 시도마다 DB 를 쓰면 오히려 공격자가 DB 부하를 유발할 수 있다.
 * 계정 잠금(더 강한 방어)은 DB 에 남으므로 재시작으로 우회되지 않는다.
 */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    /** 이 횟수를 넘으면 해당 IP 의 로그인 시도를 거부한다. */
    static final int MAX_ATTEMPTS_PER_IP = 20;

    /** 카운터가 유지되는 시간 창. */
    static final Duration WINDOW = Duration.ofMinutes(10);

    /** 메모리 무한 증식 방지 상한. 넘으면 만료 항목을 정리한다. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Attempt> attemptsByIp = new ConcurrentHashMap<>();

    /** 이 IP 가 지금 로그인을 시도할 수 있는지. */
    public boolean isAllowed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            // 출처를 특정할 수 없으면 제한을 적용하지 않는다(정상 트래픽을 막는 게 더 나쁘다).
            return true;
        }
        Attempt attempt = attemptsByIp.get(clientIp);
        if (attempt == null || attempt.isExpired()) {
            return true;
        }
        return attempt.count.get() < MAX_ATTEMPTS_PER_IP;
    }

    /** 로그인 실패를 기록한다. */
    public void recordFailure(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        cleanupIfNeeded();
        Attempt attempt = attemptsByIp.compute(clientIp, (ip, existing) ->
                (existing == null || existing.isExpired()) ? new Attempt() : existing);
        int count = attempt.count.incrementAndGet();
        if (count == MAX_ATTEMPTS_PER_IP) {
            log.warn("IP {} 의 로그인 실패가 {}회에 도달해 {} 동안 차단합니다",
                    clientIp, count, WINDOW);
        }
    }

    /** 로그인 성공 시 해당 IP 의 카운터를 지운다. */
    public void recordSuccess(String clientIp) {
        if (clientIp != null && !clientIp.isBlank()) {
            attemptsByIp.remove(clientIp);
        }
    }

    /**
     * 만료된 항목을 정리한다. 상한을 넘었을 때만 훑으므로 평상시 비용이 없다.
     * (정리하지 않으면 서로 다른 IP 가 계속 들어올 때 맵이 무한히 커진다)
     */
    private void cleanupIfNeeded() {
        if (attemptsByIp.size() < CLEANUP_THRESHOLD) {
            return;
        }
        attemptsByIp.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static final class Attempt {
        private final Instant startedAt = Instant.now();
        private final AtomicInteger count = new AtomicInteger();

        boolean isExpired() {
            return startedAt.plus(WINDOW).isBefore(Instant.now());
        }
    }
}
