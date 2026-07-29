package com.autoinstagram.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.PublishAttempt;
import com.autoinstagram.backend.post.domain.PublishAttemptRepository;
import com.autoinstagram.backend.post.service.HistoryService;
import com.autoinstagram.backend.post.service.QueueService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * POL-01 회귀 테스트: "모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함".
 *
 * <p><b>이 테스트가 존재하는 이유</b> — Phase 3 검증 중 실제로 데이터 손실이 관측됐다:
 * <pre>
 *   실패한 큐 항목 5건  vs  실패 이력 3행  vs  이력에 연결된 큐 2건
 * </pre>
 * 원인은 {@code history_records} 가 AGG-01 불변식 1(해시는 미디어별 유일) 때문에
 * 미디어당 1행만 유지하는 것이었다. 서로 다른 예약이 <b>같은 내용의 영상</b>을 가리키면
 * 두 번째 실패가 첫 번째를 덮어써 사라진다.
 *
 * <p>해결: {@code publish_attempts}(append-only)에 모든 시도를 쌓는다.
 * 이 테스트는 그 두 계층이 각자의 역할을 하는지 고정한다 —
 * 누군가 "중복이니 지우자"며 attempt 기록을 없애면 여기서 실패한다.
 */
@SpringBootTest
@TestPropertySource(properties = "app.publish.worker-enabled=false")
class Pol01AuditTrailTest {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private HistoryRecordRepository historyRepository;

    @Autowired
    private PublishAttemptRepository attemptRepository;

    @Autowired
    private QueueService queueService;

    /** 이 테스트 실행마다 고유한 해시를 쓴다 — 다른 테스트가 남긴 데이터와 섞이지 않게. */
    private String uniqueHash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64);
    }

    /**
     * 실제 예약 항목을 만들어 그 id 를 돌려준다.
     *
     * <p>임의 UUID 를 쓰면 안 되는 이유: {@code publish_attempts.queue_item_id} 에
     * 외래키 제약이 걸려 있다(V4 마이그레이션). 그 제약은 의도된 것이므로
     * 테스트가 실제 항목을 만들어야 한다.
     */
    private UUID newJob(String name) {
        return queueService.register(
                "/media/" + UUID.randomUUID() + "-" + name,
                "감사 추적 테스트",
                Instant.now().plusSeconds(3600)).getId();
    }

    @Test
    @DisplayName("같은 영상의 서로 다른 작업이 각각 실패해도 시도 기록은 하나도 사라지지 않는다")
    void everyFailureIsRecordedEvenWhenMediaHashCollides() {
        String sharedHash = uniqueHash();
        UUID firstJob = newJob("first.mp4");
        UUID secondJob = newJob("second.mp4");

        historyService.recordFailure(sharedHash, firstJob, "UPSTREAM_UNAVAILABLE",
                "첫 번째 작업 실패", true, 1);
        historyService.recordFailure(sharedHash, secondJob, "TIMEOUT",
                "두 번째 작업 실패", true, 1);

        // history_records: 미디어당 1행 (AGG-01 불변식 1 유지)
        assertThat(historyRepository.findByContentHashAndDeletedAtIsNull(sharedHash))
                .as("같은 해시는 이력 1행만 가져야 한다 (AGG-01)")
                .isPresent();

        // publish_attempts: 시도 2건 모두 남아 있어야 한다 (POL-01)
        var attempts = attemptRepository
                .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(sharedHash);

        assertThat(attempts)
                .as("실패 2건이 모두 기록되어야 한다 — 하나라도 사라지면 POL-01 위반")
                .hasSize(2);
        assertThat(attempts).extracting(PublishAttempt::getErrorCode)
                .containsExactlyInAnyOrder("UPSTREAM_UNAVAILABLE", "TIMEOUT");
        assertThat(attempts).extracting(PublishAttempt::getQueueItemId)
                .containsExactlyInAnyOrder(firstJob, secondJob);
    }

    @Test
    @DisplayName("같은 작업이 여러 번 재시도해 실패해도 각 시도가 남는다")
    void everyRetryOfSameJobIsRecorded() {
        String hash = uniqueHash();
        UUID job = newJob("job.mp4");

        historyService.recordFailure(hash, job, "TIMEOUT", "1차 실패", true, 1);
        historyService.recordFailure(hash, job, "TIMEOUT", "2차 실패", true, 2);
        historyService.recordFailure(hash, job, "UPSTREAM_UNAVAILABLE", "3차 실패", false, 3);

        var attempts = attemptRepository
                .findByQueueItemIdAndDeletedAtIsNullOrderByAttemptedAtDesc(job);

        assertThat(attempts).hasSize(3);
        // 몇 번째 시도였는지가 보존되어야 한다
        assertThat(attempts).extracting(PublishAttempt::getAttemptNumber)
                .containsExactlyInAnyOrder(1, 2, 3);
        // 마지막 시도는 재시도 예정이 아니므로 FAILED, 앞의 둘은 RETRY
        assertThat(attempts).extracting(PublishAttempt::getStatus)
                .containsExactlyInAnyOrder(
                        PublishAttempt.AttemptStatus.RETRY,
                        PublishAttempt.AttemptStatus.RETRY,
                        PublishAttempt.AttemptStatus.FAILED);
    }

    @Test
    @DisplayName("실패 후 성공하면 이력은 성공으로 바뀌지만 실패 시도 기록은 남는다")
    void successDoesNotEraseEarlierFailures() {
        String hash = uniqueHash();
        UUID job = newJob("job.mp4");

        historyService.recordFailure(hash, job, "TIMEOUT", "일시적 실패", true, 1);
        historyService.recordSuccess(hash, job, 2);

        // 현재 결과는 성공
        var record = historyRepository.findByContentHashAndDeletedAtIsNull(hash).orElseThrow();
        assertThat(record.getStatus().toApiStatus())
                .isEqualTo(com.autoinstagram.backend.post.domain.HistoryStatus.ApiHistoryStatus.SUCCESS);
        assertThat(record.getErrorCode()).as("성공 후에는 오류 코드가 지워진다").isNull();

        // 그러나 "한 번 실패했다"는 사실은 감사 추적에 남아야 한다
        var attempts = attemptRepository
                .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(hash);
        assertThat(attempts).hasSize(2);
        assertThat(attempts).extracting(PublishAttempt::getStatus)
                .containsExactlyInAnyOrder(
                        PublishAttempt.AttemptStatus.RETRY,
                        PublishAttempt.AttemptStatus.SUCCESS);
    }

    @Test
    @DisplayName("POL-05: 시도 기록의 오류 메시지에서도 토큰이 제거된다")
    void attemptMessageIsScrubbed() {
        String hash = uniqueHash();
        UUID job = newJob("job.mp4");
        String leaked = "EAAGm0PX4ZCpsBAsecretTokenValue1234567890abcdef";

        historyService.recordFailure(hash, job, "UPSTREAM_UNAVAILABLE",
                "Graph API 실패: access_token=" + leaked, false, 1);

        var attempt = attemptRepository
                .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(hash)
                .get(0);

        assertThat(attempt.getErrorMessage())
                .as("토큰이 감사 기록으로 새면 POL-05 위반")
                .doesNotContain(leaked)
                .contains("<redacted-token>");
    }

    @Test
    @DisplayName("감사 기록은 수정할 수 없다 (setter 가 없다)")
    void attemptRecordsAreAppendOnly() {
        // 감사 기록에 상태 변경 메서드가 생기면 기록의 신뢰성이 사라진다.
        // PublishAttempt 에는 의도적으로 어떤 변경 메서드도 없다.
        var mutators = java.util.Arrays.stream(PublishAttempt.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set") || name.startsWith("update")
                        || name.startsWith("mark"))
                .toList();

        assertThat(mutators)
                .as("감사 기록에 변경 메서드가 추가되었다 — append-only 원칙 위반")
                .isEmpty();
    }
}
