package com.autoinstagram.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoinstagram.backend.post.domain.HistoryStatus.ApiHistoryStatus;
import com.autoinstagram.backend.post.domain.QueueStatus.ApiQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0003 상태 값 변환 규칙 검증.
 *
 * <p>이 테스트가 지키는 것: 1_spack.md(API 응답 enum)와 2_ddd.md(애그리거트 불변식)가
 * 서로 다른 상태 목록을 요구하는 문제를 "내부는 넓게, API 는 좁게"로 해소했다.
 * 변환이 하나라도 어긋나면 API 계약이 깨지므로 전수로 고정한다.
 *
 * <p>특히 <b>API enum 에 값이 추가되는 것을 막는다</b> — 명세에 없는 상태가
 * 응답으로 나가면 화면과 문서가 어긋난다.
 */
class StatusMappingTest {

    @Test
    @DisplayName("QueueItem: 내부 4가지가 명세의 3가지로 정확히 변환된다")
    void queueStatusMapping() {
        // 2_ddd.md AGG-02 불변식: {PENDING, RUNNING, COMPLETED, FAILED}
        assertThat(QueueStatus.values()).hasSize(4);

        assertThat(QueueStatus.PENDING.toApiStatus()).isEqualTo(ApiQueueStatus.PENDING);
        // 진행 중은 호출자에게 "아직 발행 안 됨"이므로 PENDING 으로 보인다
        assertThat(QueueStatus.RUNNING.toApiStatus()).isEqualTo(ApiQueueStatus.PENDING);
        assertThat(QueueStatus.COMPLETED.toApiStatus()).isEqualTo(ApiQueueStatus.SUCCESS);
        assertThat(QueueStatus.FAILED.toApiStatus()).isEqualTo(ApiQueueStatus.FAILED);
    }

    @Test
    @DisplayName("QueueItem: API enum 은 명세가 규정한 3가지뿐이다")
    void queueApiEnumMatchesSpec() {
        // 1_spack.md API-01 응답 / ENT-02: enum PENDING|SUCCESS|FAILED
        assertThat(ApiQueueStatus.values())
                .containsExactly(ApiQueueStatus.PENDING, ApiQueueStatus.SUCCESS, ApiQueueStatus.FAILED);
    }

    @Test
    @DisplayName("HistoryRecord: 내부 3가지가 명세의 2가지로 정확히 변환된다")
    void historyStatusMapping() {
        // 2_ddd.md AGG-01 불변식: {SUCCESS, FAILED, RETRY}
        assertThat(HistoryStatus.values()).hasSize(3);

        assertThat(HistoryStatus.SUCCESS.toApiStatus()).isEqualTo(ApiHistoryStatus.SUCCESS);
        assertThat(HistoryStatus.FAILED.toApiStatus()).isEqualTo(ApiHistoryStatus.FAILED);
        // 재시도 예정도 "아직 성공하지 못함"이므로 FAILED 로 보인다
        assertThat(HistoryStatus.RETRY.toApiStatus()).isEqualTo(ApiHistoryStatus.FAILED);
    }

    @Test
    @DisplayName("HistoryRecord: API enum 은 명세가 규정한 2가지뿐이다")
    void historyApiEnumMatchesSpec() {
        // 1_spack.md ENT-01: enum SUCCESS|FAILED
        assertThat(ApiHistoryStatus.values())
                .containsExactly(ApiHistoryStatus.SUCCESS, ApiHistoryStatus.FAILED);
    }

    @Test
    @DisplayName("모든 내부 상태에 대응하는 API 상태가 반드시 있다 (누락 방지)")
    void everyInternalStatusMapsToSomething() {
        for (QueueStatus status : QueueStatus.values()) {
            assertThat(status.toApiStatus())
                    .as("QueueStatus.%s 의 변환이 비어 있다", status)
                    .isNotNull();
        }
        for (HistoryStatus status : HistoryStatus.values()) {
            assertThat(status.toApiStatus())
                    .as("HistoryStatus.%s 의 변환이 비어 있다", status)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("종료 상태 판정: COMPLETED·FAILED 만 종료다")
    void terminalStates() {
        assertThat(QueueStatus.PENDING.isTerminal()).isFalse();
        assertThat(QueueStatus.RUNNING.isTerminal()).isFalse();
        assertThat(QueueStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(QueueStatus.FAILED.isTerminal()).isTrue();
    }
}
