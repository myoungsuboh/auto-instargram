package com.autoinstagram.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AGG-01 HistoryRecord 의 도메인 불변식 검증 (2_ddd.md §2 CTX-01).
 *
 * <p>불변식 2개:
 * <ol>
 *   <li>{@code hash value must be unique per media} — 유니크 자체는 DB 인덱스가 보장하므로,
 *       여기서는 그 전제인 <b>해시 형식의 일관성</b>을 검증한다.
 *       대소문자가 섞이면 같은 미디어가 다른 값으로 저장되어 유니크가 무의미해진다.</li>
 *   <li>{@code status in {SUCCESS, FAILED, RETRY}}</li>
 * </ol>
 */
class HistoryRecordTest {

    private static final String VALID_HASH = "a".repeat(64);
    private static final UUID QUEUE_ID = UUID.randomUUID();

    @Test
    @DisplayName("성공 이력은 오류 정보 없이 생성된다")
    void createsSuccessRecord() {
        HistoryRecord record = HistoryRecord.success(VALID_HASH, QUEUE_ID);

        assertThat(record.getStatus()).isEqualTo(HistoryStatus.SUCCESS);
        assertThat(record.getContentHash()).isEqualTo(VALID_HASH);
        assertThat(record.getQueueItemId()).isEqualTo(QUEUE_ID);
        assertThat(record.getErrorCode()).isNull();
        assertThat(record.getRecordedAt()).isNotNull();
    }

    @Test
    @DisplayName("예약을 거치지 않은 직접 업로드는 queueItemId 가 null 이어도 된다")
    void allowsNullQueueItemId() {
        assertThat(HistoryRecord.success(VALID_HASH, null).getQueueItemId()).isNull();
    }

    @Test
    @DisplayName("POL-01: 실패 이력에는 errorCode 가 반드시 있어야 한다")
    void failureRequiresErrorCode() {
        // 원인 없는 실패 기록은 추적 가능성이 없어 POL-01 의 목적을 달성하지 못한다
        assertThatThrownBy(() -> HistoryRecord.failure(VALID_HASH, QUEUE_ID, null, "메시지"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCode");
        assertThatThrownBy(() -> HistoryRecord.failure(VALID_HASH, QUEUE_ID, "  ", "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoryRecord.retrying(VALID_HASH, QUEUE_ID, null, "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("불변식 2: 재시도 예정 이력은 내부 상태가 RETRY 다")
    void retryingHasRetryStatus() {
        HistoryRecord record = HistoryRecord.retrying(VALID_HASH, QUEUE_ID, "TIMEOUT", "일시적 실패");

        assertThat(record.getStatus()).isEqualTo(HistoryStatus.RETRY);
        // ADR-0003: 하지만 API 에서는 FAILED 로 보인다
        assertThat(record.getStatus().toApiStatus())
                .isEqualTo(HistoryStatus.ApiHistoryStatus.FAILED);
    }

    @Test
    @DisplayName("불변식 1 전제: 해시는 정확히 64자여야 한다")
    void rejectsWrongHashLength() {
        assertThatThrownBy(() -> HistoryRecord.success("tooshort", QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
        assertThatThrownBy(() -> HistoryRecord.success("a".repeat(65), QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoryRecord.success(null, QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("불변식 1 전제: 대문자 hex 를 거부한다 (대소문자 혼용 시 중복 방지가 뚫린다)")
    void rejectsUppercaseHash() {
        // 같은 미디어가 'ABC...' 와 'abc...' 로 두 번 저장되면 유니크 인덱스가 무의미해진다
        assertThatThrownBy(() -> HistoryRecord.success("A".repeat(64), QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소문자");
    }

    @Test
    @DisplayName("불변식 1 전제: hex 가 아닌 문자를 거부한다")
    void rejectsNonHexHash() {
        assertThatThrownBy(() -> HistoryRecord.success("z".repeat(64), QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoryRecord.success("-".repeat(64), QUEUE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결과 갱신: 실패 → 성공이면 오류 정보가 지워진다")
    void updateToSuccessClearsError() {
        HistoryRecord record = HistoryRecord.failure(VALID_HASH, QUEUE_ID, "TIMEOUT", "일시적 실패");

        record.updateOutcome(HistoryStatus.SUCCESS, null, null);

        assertThat(record.getStatus()).isEqualTo(HistoryStatus.SUCCESS);
        assertThat(record.getErrorCode()).isNull();
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("결과 갱신: 실패·재시도로 바꿀 때는 errorCode 가 필요하다")
    void updateToFailureRequiresErrorCode() {
        HistoryRecord record = HistoryRecord.success(VALID_HASH, QUEUE_ID);

        assertThatThrownBy(() -> record.updateOutcome(HistoryStatus.FAILED, null, "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.updateOutcome(null, "CODE", "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결과 갱신 시 기록 시각이 갱신된다")
    void updateRefreshesTimestamp() throws InterruptedException {
        HistoryRecord record = HistoryRecord.failure(VALID_HASH, QUEUE_ID, "TIMEOUT", null);
        var before = record.getRecordedAt();

        Thread.sleep(5);
        record.updateOutcome(HistoryStatus.SUCCESS, null, null);

        assertThat(record.getRecordedAt()).isAfter(before);
    }

    @Test
    @DisplayName("toString 에 오류 메시지 본문이 들어가지 않는다")
    void toStringHidesErrorMessage() {
        HistoryRecord record = HistoryRecord.failure(
                VALID_HASH, QUEUE_ID, "UPSTREAM", "민감할 수 있는 외부 응답 본문");

        assertThat(record.toString()).doesNotContain("민감할 수 있는");
        // 코드는 남는다 — 추적에 필요하고 비밀이 아니다
        assertThat(record.toString()).contains("UPSTREAM");
    }
}
