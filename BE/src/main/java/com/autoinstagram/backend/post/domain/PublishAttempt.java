package com.autoinstagram.backend.post.domain;

import com.autoinstagram.backend.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * POL-01 감사 추적 — 게시 시도 한 건의 기록.
 *
 * <p><b>{@link HistoryRecord} 와의 차이</b>:
 * <table>
 *   <tr><th></th><th>HistoryRecord</th><th>PublishAttempt</th></tr>
 *   <tr><td>단위</td><td>미디어당 1행</td><td>시도당 1행</td></tr>
 *   <tr><td>유니크</td><td>content_hash 유니크 (AGG-01 불변식 1)</td><td>없음 (의도적)</td></tr>
 *   <tr><td>갱신</td><td>결과가 바뀌면 덮어씀</td><td>append-only, 절대 수정하지 않음</td></tr>
 *   <tr><td>목적</td><td>중복 업로드 방지 + API-03 조회</td><td>POL-01 누락 없는 감사 추적</td></tr>
 * </table>
 *
 * <p>이 테이블이 필요한 이유는 실측으로 드러났다 — 서로 다른 예약이 같은 영상을 가리키면
 * HistoryRecord 는 한 행을 공유해 앞선 실패가 지워진다(Phase 3 검증에서 실패 5건 중 3건 소실 관측).
 * skills/db/soft-delete-soft-delete-audit.md 규칙 6("변경 이력은 별도 테이블")에 따라 분리했다.
 *
 * <p>불변 객체로 만든 이유: 감사 기록은 나중에 수정되면 감사 가치가 없다.
 * 상태 변경 메서드를 아예 제공하지 않는다.
 */
@Entity
@Table(name = "publish_attempts")
public class PublishAttempt extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "queue_item_id", updatable = false)
    private UUID queueItemId;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private AttemptStatus status;

    @Column(name = "error_code", length = 100, updatable = false)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text", updatable = false)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    protected PublishAttempt() {
        // JPA 전용
    }

    private PublishAttempt(UUID id, UUID queueItemId, String contentHash, AttemptStatus status,
                           String errorCode, String errorMessage, int attemptNumber) {
        this.id = id;
        this.queueItemId = queueItemId;
        this.contentHash = contentHash;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attemptedAt = Instant.now();
        this.attemptNumber = attemptNumber;
    }

    /** 시도 시작. */
    public static PublishAttempt started(UUID queueItemId, String contentHash, int attemptNumber) {
        return create(queueItemId, contentHash, AttemptStatus.STARTED, null, null, attemptNumber);
    }

    /** 시도 성공. */
    public static PublishAttempt succeeded(UUID queueItemId, String contentHash, int attemptNumber) {
        return create(queueItemId, contentHash, AttemptStatus.SUCCESS, null, null, attemptNumber);
    }

    /**
     * 시도 실패 (POL-01).
     *
     * @param errorMessage 반드시 마스킹을 거친 문자열 (POL-05)
     */
    public static PublishAttempt failed(UUID queueItemId, String contentHash, String errorCode,
                                        String errorMessage, int attemptNumber, boolean retryPlanned) {
        if (errorCode == null || errorCode.isBlank()) {
            // 원인 없는 실패 기록은 감사 가치가 없다
            throw new IllegalArgumentException("실패 기록에는 errorCode 가 필요합니다");
        }
        return create(queueItemId, contentHash,
                retryPlanned ? AttemptStatus.RETRY : AttemptStatus.FAILED,
                errorCode, errorMessage, attemptNumber);
    }

    private static PublishAttempt create(UUID queueItemId, String contentHash, AttemptStatus status,
                                         String errorCode, String errorMessage, int attemptNumber) {
        if (contentHash == null || contentHash.length() != 64) {
            throw new IllegalArgumentException("contentHash 는 64자 SHA-256 hex 여야 합니다");
        }
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber 는 0 이상이어야 합니다");
        }
        return new PublishAttempt(UUID.randomUUID(), queueItemId, contentHash, status,
                errorCode, errorMessage, attemptNumber);
    }

    public UUID getId() {
        return id;
    }

    public UUID getQueueItemId() {
        return queueItemId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    /** errorMessage 본문은 로그에 재출력하지 않는다. */
    @Override
    public String toString() {
        return "PublishAttempt{id=" + id
                + ", queueItemId=" + queueItemId
                + ", status=" + status
                + ", attemptNumber=" + attemptNumber
                + ", errorCode=" + errorCode + "}";
    }

    /** 시도 상태. {@link HistoryStatus} 의 3종 + 시작 시점을 나타내는 STARTED. */
    public enum AttemptStatus {
        STARTED,
        SUCCESS,
        FAILED,
        RETRY
    }
}
