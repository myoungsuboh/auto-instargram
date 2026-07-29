package com.autoinstagram.backend.post.domain;

/**
 * QueueItem 의 내부 상태 (AGG-02 불변식 1: {@code queue status in {PENDING, RUNNING, COMPLETED, FAILED}}).
 *
 * <p><b>내부 상태와 API 응답이 다르다</b> — ADR-0003 의 확정 결정이다.
 * 1_spack.md 는 API 응답 enum 을 {@code PENDING|SUCCESS|FAILED} 로 규정하고,
 * 2_ddd.md 는 애그리거트 불변식으로 4가지 상태를 요구한다.
 * 두 문서를 동시에 만족시키기 위해 저장은 4가지로 하고 응답 직전에 3가지로 변환한다.
 *
 * <p>변환을 {@link #toApiStatus()} 로 enum 안에 두는 이유: 컨트롤러나 DTO 각자가 매핑하면
 * 한 군데서 빠뜨렸을 때 계약이 조용히 깨진다. 변환 규칙의 소유자를 하나로 둔다.
 */
public enum QueueStatus {

    /** 예약됐고 아직 시작하지 않음. */
    PENDING(ApiQueueStatus.PENDING),

    /** 업로드 파이프라인 진행 중. API 응답에서는 PENDING 으로 보인다. */
    RUNNING(ApiQueueStatus.PENDING),

    /** 게시 완료. API 응답에서는 SUCCESS 로 보인다. */
    COMPLETED(ApiQueueStatus.SUCCESS),

    /** 실패. */
    FAILED(ApiQueueStatus.FAILED);

    private final ApiQueueStatus apiStatus;

    QueueStatus(ApiQueueStatus apiStatus) {
        this.apiStatus = apiStatus;
    }

    /** 1_spack.md 가 규정한 API 응답용 상태. */
    public ApiQueueStatus toApiStatus() {
        return apiStatus;
    }

    /** 더 이상 진행하지 않는 종료 상태인지. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * 1_spack.md API-01/ENT-02 가 규정한 응답 enum: {@code PENDING|SUCCESS|FAILED}.
     * 이 목록에 값을 추가하면 명세와 어긋난다.
     */
    public enum ApiQueueStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
