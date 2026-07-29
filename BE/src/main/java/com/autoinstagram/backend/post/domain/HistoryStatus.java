package com.autoinstagram.backend.post.domain;

/**
 * HistoryRecord 의 내부 상태 (AGG-01 불변식 2: {@code status in {SUCCESS, FAILED, RETRY}}).
 *
 * <p>ADR-0003: 1_spack.md ENT-01 은 {@code SUCCESS|FAILED} 만 규정하고
 * 2_ddd.md AGG-01 은 {@code RETRY} 를 포함한 3가지를 요구한다.
 * 저장은 3가지, API 응답은 2가지로 변환한다.
 */
public enum HistoryStatus {

    /** 게시 성공. */
    SUCCESS(ApiHistoryStatus.SUCCESS),

    /** 게시 실패 (재시도 예정 없음). */
    FAILED(ApiHistoryStatus.FAILED),

    /**
     * 실패했으나 재시도 예정.
     * API 응답에서는 FAILED 로 보인다 — 아직 성공하지 못했다는 점에서 호출자에게는 실패다.
     * 재시도 여부는 별도 필드({@code retryCount})로 드러낸다.
     */
    RETRY(ApiHistoryStatus.FAILED);

    private final ApiHistoryStatus apiStatus;

    HistoryStatus(ApiHistoryStatus apiStatus) {
        this.apiStatus = apiStatus;
    }

    /** 1_spack.md 가 규정한 API 응답용 상태. */
    public ApiHistoryStatus toApiStatus() {
        return apiStatus;
    }

    /**
     * 1_spack.md ENT-01 이 규정한 응답 enum: {@code SUCCESS|FAILED}.
     * 이 목록에 값을 추가하면 명세와 어긋난다.
     */
    public enum ApiHistoryStatus {
        SUCCESS,
        FAILED
    }
}
