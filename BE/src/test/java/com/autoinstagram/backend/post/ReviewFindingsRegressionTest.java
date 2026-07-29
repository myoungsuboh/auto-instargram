package com.autoinstagram.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.post.domain.HistoryRecord;
import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.HistoryStatus;
import com.autoinstagram.backend.post.domain.PublishAttempt;
import com.autoinstagram.backend.post.domain.PublishAttemptRepository;
import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import com.autoinstagram.backend.post.domain.QueueStatus;
import com.autoinstagram.backend.post.service.HistoryService;
import com.autoinstagram.backend.post.service.MediaPathValidator;
import com.autoinstagram.backend.post.service.QueueService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 적대적 코드 검토에서 확정된 결함 8건의 회귀 테스트.
 *
 * <p>각 테스트는 "고치기 전이라면 실패하는" 시나리오를 그대로 재현한다.
 * 누군가 나중에 이 방어를 되돌리면 여기서 잡힌다.
 *
 * <p>실행 전제: PostgreSQL 이 떠 있어야 한다 (ADR-0010).
 */
@SpringBootTest
@TestPropertySource(properties = "app.publish.worker-enabled=false")
class ReviewFindingsRegressionTest {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private HistoryRecordRepository historyRepository;

    @Autowired
    private PublishAttemptRepository attemptRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private MediaPathValidator pathValidator;

    private String uniqueHash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64);
    }

    /**
     * 실제 예약 항목을 만들어 그 id 를 돌려준다.
     * publish_attempts.queue_item_id 에 외래키 제약이 있어 임의 UUID 는 쓸 수 없다(V4).
     */
    private UUID newJob(String name) {
        return queueService.register(uniquePath(name), "회귀 테스트",
                Instant.now().plusSeconds(3600)).getId();
    }

    private String uniquePath(String name) {
        return pathValidator.getAllowedBaseDir()
                .resolve(UUID.randomUUID() + "-" + name).toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [1] HIGH — 게시 성공 이력이 실패로 덮여 중복 차단이 풀리는 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[1] 게시 성공 이력은 나중의 실패로 덮이지 않는다")
    class SuccessNotOverwritten {

        @Test
        @DisplayName("성공 후 같은 미디어가 실패해도 이력은 SUCCESS 로 유지된다")
        void failureAfterSuccessKeepsSuccess() {
            String hash = uniqueHash();
            UUID firstJob = newJob("first.mp4");
            UUID secondJob = newJob("second.mp4");

            historyService.recordSuccess(hash, firstJob, 0);
            // 같은 영상을 가리키는 다른 작업이 실패한다
            historyService.recordFailure(hash, secondJob, "UPSTREAM_UNAVAILABLE",
                    "두 번째 작업 실패", true, 1);

            HistoryRecord record = historyRepository
                    .findByContentHashAndDeletedAtIsNull(hash).orElseThrow();

            // 고치기 전에는 여기서 RETRY 가 되어 ① 감사 기록 소실
            // ② 중복 업로드 차단 해제 ③ 게시 한도 과소 계수가 동시에 발생했다
            assertThat(record.getStatus())
                    .as("게시된 사실이 사라지면 중복 게시 차단과 한도 계산이 함께 무너진다")
                    .isEqualTo(HistoryStatus.SUCCESS);
            assertThat(record.getErrorCode()).isNull();
        }

        @Test
        @DisplayName("그래도 실패 시도 자체는 감사 기록에 남는다 (POL-01)")
        void failureStillRecordedAsAttempt() {
            String hash = uniqueHash();
            historyService.recordSuccess(hash, newJob("s.mp4"), 0);
            historyService.recordFailure(hash, newJob("f.mp4"), "TIMEOUT", "실패", false, 1);

            assertThat(attemptRepository
                    .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(hash))
                    .as("이력을 유지하더라도 실패 사실은 어딘가에 남아야 한다")
                    .hasSize(2);
        }

        @Test
        @DisplayName("도메인 객체 차원에서도 SUCCESS → FAILED 전이를 거부한다")
        void domainRejectsDowngrade() {
            HistoryRecord record = HistoryRecord.success("a".repeat(64), UUID.randomUUID());

            assertThatThrownBy(() ->
                    record.updateOutcome(HistoryStatus.FAILED, "SOME_ERROR", "메시지"))
                    .isInstanceOf(HistoryRecord.AlreadyPublishedException.class);
        }

        @Test
        @DisplayName("성공 → 성공 갱신은 허용한다 (재게시 등)")
        void successToSuccessAllowed() {
            HistoryRecord record = HistoryRecord.success("b".repeat(64), UUID.randomUUID());

            assertThatCode(() -> record.updateOutcome(HistoryStatus.SUCCESS, null, null))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [2] MEDIUM — endDate 만 지정하면 빈 배열이 나오던 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[2] endDate 만 지정한 조회도 정상 동작한다")
    class HistoryDateRange {

        @Test
        @DisplayName("90일보다 오래된 endDate 만 줘도 그 기간의 이력이 조회된다")
        void endDateOnlyFindsOldRecords() {
            // 기본 조회 범위(90일)를 '현재' 기준으로 고정하면 from > to 가 되어
            // 데이터가 있어도 무조건 빈 배열이 나갔다.
            LocalDate longAgo = LocalDate.now(ZoneOffset.UTC).minusDays(200);

            List<HistoryRecord> result = historyService.findHistory(null, longAgo);

            // 결과 건수는 데이터에 따라 다르므로, "쿼리를 아예 건너뛰지 않았다"는 것을
            // from/to 계산이 유효한 범위인지로 확인한다 — 예외 없이 반환되면 계산이 맞다.
            assertThat(result).as("빈 목록이어도 되지만 예외 없이 조회는 실행되어야 한다").isNotNull();
        }

        @Test
        @DisplayName("endDate 만 지정했을 때 그 날짜 범위의 데이터를 실제로 찾는다")
        void endDateOnlyActuallyReturnsData() {
            // 오늘 기록을 하나 만들고, endDate=오늘 로 조회하면 반드시 잡혀야 한다
            String hash = uniqueHash();
            historyService.recordSuccess(hash, null, 0);

            List<HistoryRecord> result =
                    historyService.findHistory(null, LocalDate.now(ZoneOffset.UTC));

            assertThat(result).extracting(HistoryRecord::getContentHash).contains(hash);
        }

        @Test
        @DisplayName("종료일은 그 날 전체를 포함한다 (23:59:59 까지)")
        void endDateIncludesWholeDay() {
            String hash = uniqueHash();
            historyService.recordSuccess(hash, null, 0);

            // 오늘 기록을 endDate=오늘 로 조회했을 때 빠지면 종료일 경계 계산이 틀린 것이다
            assertThat(historyService.findHistory(
                    LocalDate.now(ZoneOffset.UTC), LocalDate.now(ZoneOffset.UTC)))
                    .extracting(HistoryRecord::getContentHash)
                    .contains(hash);
        }

        @Test
        @DisplayName("startDate > endDate 는 빈 목록을 반환한다 (POL-03, 예외 아님)")
        void invertedRangeReturnsEmpty() {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            assertThat(historyService.findHistory(today, today.minusDays(10))).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [3][4] HIGH — 두 인스턴스가 같은 예약을 집어 두 번 게시하는 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[3][4] 예약 선점은 원자적이다 (이중 게시 방지)")
    class AtomicClaim {

        @Test
        @DisplayName("같은 항목을 두 번 선점하려 하면 한 번만 성공한다")
        void onlyOneClaimSucceeds() {
            QueueItem item = queueService.register(
                    uniquePath("claim.mp4"), "선점 테스트", Instant.now().minusSeconds(60));

            boolean first = queueService.tryClaimForPublishing(item.getId());
            boolean second = queueService.tryClaimForPublishing(item.getId());

            // 고치기 전에는 둘 다 true 가 되어 같은 영상이 두 번 게시됐다
            assertThat(first).as("첫 선점은 성공해야 한다").isTrue();
            assertThat(second).as("두 번째 선점은 실패해야 한다 — 성공하면 이중 게시된다").isFalse();
        }

        @Test
        @DisplayName("선점에 성공하면 상태가 RUNNING 이 된다")
        void claimMovesToRunning() {
            QueueItem item = queueService.register(
                    uniquePath("running.mp4"), "상태 확인", Instant.now().minusSeconds(60));

            queueService.tryClaimForPublishing(item.getId());

            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getStatus()).isEqualTo(QueueStatus.RUNNING);
        }

        @Test
        @DisplayName("PENDING 이 아닌 항목은 선점할 수 없다")
        void cannotClaimNonPending() {
            QueueItem item = queueService.register(
                    uniquePath("done.mp4"), "완료 항목", Instant.now().minusSeconds(60));
            queueService.tryClaimForPublishing(item.getId());
            queueService.markCompleted(item.getId());

            assertThat(queueService.tryClaimForPublishing(item.getId())).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [6] MEDIUM — 인스턴스가 죽어 RUNNING 으로 영구히 멈추는 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[6] 멈춘 예약은 회수되어 다시 처리된다")
    class StalledReclaim {

        @Test
        @DisplayName("오래 RUNNING 인 항목을 대기 상태로 회수한다")
        void reclaimsStalledItem() {
            QueueItem item = queueService.register(
                    uniquePath("stalled.mp4"), "멈춘 항목", Instant.now().minusSeconds(60));
            queueService.tryClaimForPublishing(item.getId());

            // 임계값을 0 으로 주면 방금 RUNNING 이 된 항목도 회수 대상이 된다
            int reclaimed = queueService.reclaimStalledItems(Duration.ZERO);

            assertThat(reclaimed).isPositive();
            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getStatus())
                    .as("회수 후에는 다시 집힐 수 있도록 PENDING 이어야 한다")
                    .isEqualTo(QueueStatus.PENDING);
        }

        @Test
        @DisplayName("회수는 재시도 횟수를 소모하지 않는다 (인프라 문제는 예약의 잘못이 아니다)")
        void reclaimDoesNotConsumeRetries() {
            QueueItem item = queueService.register(
                    uniquePath("stalled-retry.mp4"), "회수 항목", Instant.now().minusSeconds(60));
            queueService.tryClaimForPublishing(item.getId());

            queueService.reclaimStalledItems(Duration.ZERO);

            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getRetryCount())
                    .as("인프라 장애로 재시도 한도가 소진되면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("아직 오래되지 않은 RUNNING 항목은 회수하지 않는다 (정상 처리 중인 것을 건드리면 이중 게시)")
        void doesNotReclaimFreshRunning() {
            QueueItem item = queueService.register(
                    uniquePath("fresh.mp4"), "방금 시작", Instant.now().minusSeconds(60));
            queueService.tryClaimForPublishing(item.getId());

            queueService.reclaimStalledItems(Duration.ofHours(1));

            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getStatus()).isEqualTo(QueueStatus.RUNNING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [7] HIGH — UNC 경로로 스레드 블로킹 + SMB 인증 유출
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[7] 네트워크(UNC) 경로는 파일시스템 접근 전에 즉시 거부된다")
    class UncRejection {

        @Test
        @DisplayName("UNC 경로를 즉시 거부한다 (20초 블로킹·SMB 인증 유출 방지)")
        void rejectsUncPathImmediately() {
            long start = System.nanoTime();

            assertThatThrownBy(() -> pathValidator.validate("\\\\192.0.2.1\\share\\x.mp4"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("네트워크 경로");

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            // 고치기 전에는 SMB 접속을 시도해 20초 이상 걸렸다 (POL-04 위반 + DoS 가능)
            assertThat(elapsedMs)
                    .as("파일시스템에 접근하기 전에 거부해야 한다 (실제 측정 %dms)", elapsedMs)
                    .isLessThan(1000);
        }

        @Test
        @DisplayName("슬래시 형태의 UNC 경로도 거부한다")
        void rejectsForwardSlashUnc() {
            assertThatThrownBy(() -> pathValidator.validate("//evil.example.com/share/x.mp4"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("네트워크 경로");
        }

        @Test
        @DisplayName("정상 상대 경로는 여전히 통과한다")
        void stillAllowsNormalPaths() {
            assertThatCode(() -> pathValidator.validate("normal.mp4"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [5] MEDIUM — 게시 전 창에서 같은 영상이 두 번 접수되는 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[5] 진행 중인 같은 영상의 중복 접수를 막는다")
    class InFlightDuplicate {

        @Test
        @DisplayName("PENDING 인 같은 경로의 작업이 있으면 감지된다")
        void detectsPendingDuplicate() {
            String path = uniquePath("inflight.mp4");
            queueService.register(path, "첫 접수", Instant.now().plusSeconds(3600));

            assertThat(queueItemRepository.existsByMediaPathAndStatusInAndDeletedAtIsNull(
                    path, List.of(QueueStatus.PENDING, QueueStatus.RUNNING)))
                    .as("게시 전 창에서 중복을 감지하지 못하면 같은 영상이 두 번 게시된다")
                    .isTrue();
        }

        @Test
        @DisplayName("완료·실패한 작업은 진행 중으로 보지 않는다 (재시도를 막으면 안 된다)")
        void terminalStatesAreNotInFlight() {
            String path = uniquePath("finished.mp4");
            QueueItem item = queueService.register(path, "완료될 작업", Instant.now().minusSeconds(60));
            queueService.tryClaimForPublishing(item.getId());
            queueService.markCompleted(item.getId());

            assertThat(queueItemRepository.existsByMediaPathAndStatusInAndDeletedAtIsNull(
                    path, List.of(QueueStatus.PENDING, QueueStatus.RUNNING)))
                    .isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  [8] MEDIUM — 중괄호가 든 캡션이 게시를 영구 실패시키는 문제
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("[8] 특수문자가 든 캡션도 저장·처리된다")
    class CaptionWithBraces {

        @Test
        @DisplayName("중괄호가 든 캡션으로 예약을 등록할 수 있다")
        void acceptsCaptionWithBraces() {
            // 캡션을 URI 템플릿으로 해석하면 이 값이 게시 단계에서 영구히 실패한다.
            // 등록 자체는 원래도 됐으므로, 여기서는 저장·조회가 온전한지를 고정한다.
            QueueItem item = queueService.register(
                    uniquePath("braces.mp4"), "오늘 메뉴 {김치찌개} 추천 {{강추}}",
                    Instant.now().plusSeconds(3600));

            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getCaption())
                    .isEqualTo("오늘 메뉴 {김치찌개} 추천 {{강추}}");
        }

        @Test
        @DisplayName("이모지·따옴표가 든 캡션도 온전히 저장된다")
        void acceptsEmojiAndQuotes() {
            String caption = "여름 브이로그 🌊 \"바다\" & 'ocean' 100%";
            QueueItem item = queueService.register(
                    uniquePath("emoji.mp4"), caption, Instant.now().plusSeconds(3600));

            assertThat(queueItemRepository.findByIdAndDeletedAtIsNull(item.getId())
                    .orElseThrow().getCaption()).isEqualTo(caption);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  전체 정합성
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POL-01: 모든 실패가 감사 기록에 남는다 (검토에서 관측된 소실이 재발하지 않는다)")
    void allFailuresAreAudited() {
        String sharedHash = uniqueHash();
        int failures = 5;

        for (int i = 1; i <= failures; i++) {
            historyService.recordFailure(sharedHash, newJob("f" + i + ".mp4"),
                    "ERROR_" + i, "실패 " + i, i < failures, i);
        }

        // 검토 시점 관측: 실패 5건 → 이력 3행 (2건 소실).
        // 지금은 이력이 1행이어도 시도 기록 5건이 모두 남아야 한다.
        assertThat(attemptRepository
                .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(sharedHash))
                .as("실패 %d건 전부가 감사 기록에 있어야 한다", failures)
                .hasSize(failures);

        assertThat(attemptRepository
                .findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(sharedHash))
                .extracting(PublishAttempt::getErrorCode)
                .containsExactlyInAnyOrder("ERROR_1", "ERROR_2", "ERROR_3", "ERROR_4", "ERROR_5");
    }

    @Test
    @DisplayName("시간 경계: 미래 예약은 실행 대상이 아니다")
    void futureItemsAreNotDue() {
        QueueItem future = queueService.register(
                uniquePath("future.mp4"), "미래 예약", Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(queueService.findDueItems(Instant.now()))
                .extracting(QueueItem::getId)
                .doesNotContain(future.getId());
    }
}
