package com.autoinstagram.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AGG-02 QueueItem 의 도메인 불변식 검증 (2_ddd.md §2 CTX-01).
 *
 * <p>불변식 2개:
 * <ol>
 *   <li>{@code queue status in {PENDING, RUNNING, COMPLETED, FAILED}}</li>
 *   <li>{@code retryCount >= 0}</li>
 * </ol>
 */
class QueueItemTest {

    private static final String PATH = "/media/reel.mp4";
    private static final Instant TOMORROW = Instant.now().plus(1, ChronoUnit.DAYS);

    private QueueItem newItem() {
        return QueueItem.schedule(PATH, "캡션", TOMORROW);
    }

    @Test
    @DisplayName("새 예약은 PENDING 이고 재시도 0회로 시작한다")
    void startsPending() {
        QueueItem item = newItem();

        assertThat(item.getStatus()).isEqualTo(QueueStatus.PENDING);
        assertThat(item.getRetryCount()).isZero();
        assertThat(item.getId()).isNotNull();
        assertThat(item.getLastErrorCode()).isNull();
    }

    @Test
    @DisplayName("필수 값이 없거나 길이를 넘기면 생성을 거부한다 (fail-closed)")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> QueueItem.schedule(" ", "캡션", TOMORROW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QueueItem.schedule(PATH, "캡션", null))
                .isInstanceOf(IllegalArgumentException.class);

        String tooLongPath = "/".repeat(QueueItem.MAX_MEDIA_PATH_LENGTH + 1);
        assertThatThrownBy(() -> QueueItem.schedule(tooLongPath, "캡션", TOMORROW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(QueueItem.MAX_MEDIA_PATH_LENGTH));

        String tooLongCaption = "가".repeat(QueueItem.MAX_CAPTION_LENGTH + 1);
        assertThatThrownBy(() -> QueueItem.schedule(PATH, tooLongCaption, TOMORROW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("캡션은 선택 항목이다 (1_spack.md API-01: 필수 아님)")
    void captionIsOptional() {
        assertThat(QueueItem.schedule(PATH, null, TOMORROW).getCaption()).isNull();
    }

    @Test
    @DisplayName("불변식 1: 정상 상태 전이 PENDING → RUNNING → COMPLETED")
    void happyPathTransition() {
        QueueItem item = newItem();

        item.markRunning();
        assertThat(item.getStatus()).isEqualTo(QueueStatus.RUNNING);

        item.markCompleted();
        assertThat(item.getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(item.getStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("불변식 1: 허용되지 않은 전이는 거부한다 (임의 상태 변경 차단)")
    void rejectsIllegalTransition() {
        QueueItem item = newItem();

        // PENDING 에서 바로 완료로 갈 수 없다
        assertThatThrownBy(item::markCompleted)
                .isInstanceOf(IllegalStateException.class);

        item.markRunning();
        // 이미 RUNNING 인데 다시 시작할 수 없다
        assertThatThrownBy(item::markRunning)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("불변식 2: 실패할 때마다 재시도 횟수가 늘고 절대 음수가 되지 않는다")
    void retryCountOnlyIncreases() {
        QueueItem item = newItem();

        item.markRunning();
        item.markFailed("UPSTREAM_UNAVAILABLE");

        assertThat(item.getStatus()).isEqualTo(QueueStatus.FAILED);
        assertThat(item.getRetryCount()).isEqualTo(1);
        assertThat(item.getLastErrorCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(item.getLastFailedAt()).isNotNull();

        item.requeueForRetry();
        assertThat(item.getStatus()).isEqualTo(QueueStatus.PENDING);
        // 재시도로 되돌려도 횟수는 유지된다 — 몇 번 실패했는지가 사라지면 안 된다
        assertThat(item.getRetryCount()).isEqualTo(1);

        item.markRunning();
        item.markFailed("TIMEOUT");
        assertThat(item.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("완료된 예약은 실패로 되돌릴 수 없다")
    void completedCannotFail() {
        QueueItem item = newItem();
        item.markRunning();
        item.markCompleted();

        assertThatThrownBy(() -> item.markFailed("SOME_ERROR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된");
    }

    @Test
    @DisplayName("성공하면 이전 실패 코드가 지워진다")
    void successClearsErrorCode() {
        QueueItem item = newItem();
        item.markRunning();
        item.markFailed("TEMP_ERROR");
        item.requeueForRetry();
        item.markRunning();
        item.markCompleted();

        assertThat(item.getLastErrorCode()).isNull();
    }

    @Test
    @DisplayName("isDue: 예약 시각이 지난 PENDING 항목만 실행 대상이다")
    void isDueOnlyForPastPending() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        QueueItem duePast = QueueItem.schedule(PATH, null, past);
        QueueItem future = QueueItem.schedule(PATH, null, TOMORROW);

        assertThat(duePast.isDue(Instant.now())).isTrue();
        assertThat(future.isDue(Instant.now())).isFalse();

        // 이미 실행 중인 항목은 다시 집어 가면 안 된다 (중복 게시 위험)
        duePast.markRunning();
        assertThat(duePast.isDue(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("toString 에 캡션이 들어가지 않는다 (사용자 콘텐츠를 로그에 남기지 않는다)")
    void toStringHidesCaption() {
        QueueItem item = QueueItem.schedule(PATH, "비밀스러운 캡션 내용", TOMORROW);

        assertThat(item.toString()).doesNotContain("비밀스러운");
    }
}
