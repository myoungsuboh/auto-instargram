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
 * ENT-01 / AGG-01 — 게시 이력 및 중복 업로드 방지 애그리거트 루트.
 * (구 {@code history.json} 을 RDBMS 로 이관 — 3_architecture.md DB-01)
 *
 * <p>2_ddd.md AGG-01 도메인 규칙(불변식) 2개:
 * <ol>
 *   <li>{@code hash value must be unique per media}
 *       → 활성 레코드 한정 unique index {@code ux_history_records_content_hash}</li>
 *   <li>{@code status in {SUCCESS, FAILED, RETRY}}
 *       → {@link HistoryStatus} enum + DB CHECK {@code ck_history_records_status}</li>
 * </ol>
 *
 * <p>정책:
 * <ul>
 *   <li>POL-01 — 모든 실패 경로를 누락 없이 기록한다. 그래서 {@link #failure} 팩토리가 존재하고
 *       {@code errorCode}/{@code errorMessage} 를 보관한다.</li>
 *   <li>POL-02 — 쓰기는 원자적이어야 한다. 파일이 아니라 DB 트랜잭션으로 보장된다
 *       (파일 기반 history.json 의 동시성 문제가 이관의 이유였다).</li>
 *   <li>POL-05 — {@code errorMessage} 에 토큰이 섞이지 않도록 저장 전 마스킹한다(서비스 책임).</li>
 * </ul>
 */
@Entity
@Table(name = "history_records")
public class HistoryRecord extends BaseEntity {

    /** SHA-256 hex 길이. 1_spack.md ENT-01: contentHash len=64 */
    public static final int CONTENT_HASH_LENGTH = 64;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content_hash", nullable = false, length = CONTENT_HASH_LENGTH, updatable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HistoryStatus status;

    /**
     * 1_spack.md ENT-01 의 {@code timestamp}.
     * 컬럼명이 {@code recorded_at} 인 이유는 {@code timestamp} 가 SQL 예약어이기 때문이다(ADR-0004).
     * API 응답 필드명은 {@code timestamp} 를 유지한다.
     */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** 이 이력을 만든 예약 큐 항목. 예약을 거치지 않은 직접 업로드는 null. */
    @Column(name = "queue_item_id")
    private UUID queueItemId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected HistoryRecord() {
        // JPA 전용
    }

    private HistoryRecord(UUID id, String contentHash, HistoryStatus status, Instant recordedAt,
                          UUID queueItemId, String errorCode, String errorMessage) {
        this.id = id;
        this.contentHash = contentHash;
        this.status = status;
        this.recordedAt = recordedAt;
        this.queueItemId = queueItemId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /** 게시 성공 이력. */
    public static HistoryRecord success(String contentHash, UUID queueItemId) {
        validateHash(contentHash);
        return new HistoryRecord(UUID.randomUUID(), contentHash, HistoryStatus.SUCCESS,
                Instant.now(), queueItemId, null, null);
    }

    /**
     * 실패 이력 (POL-01).
     *
     * @param errorMessage 반드시 마스킹을 거친 문자열을 넘길 것 (POL-05)
     */
    public static HistoryRecord failure(String contentHash, UUID queueItemId,
                                        String errorCode, String errorMessage) {
        validateHash(contentHash);
        if (errorCode == null || errorCode.isBlank()) {
            // 실패 이력에 원인이 없으면 POL-01 의 목적(추적 가능성)을 달성하지 못한다
            throw new IllegalArgumentException("실패 이력에는 errorCode 가 필요합니다");
        }
        return new HistoryRecord(UUID.randomUUID(), contentHash, HistoryStatus.FAILED,
                Instant.now(), queueItemId, errorCode, errorMessage);
    }

    /** 재시도 예정 이력. API 응답에서는 FAILED 로 보인다. */
    public static HistoryRecord retrying(String contentHash, UUID queueItemId,
                                         String errorCode, String errorMessage) {
        validateHash(contentHash);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("재시도 이력에는 errorCode 가 필요합니다");
        }
        return new HistoryRecord(UUID.randomUUID(), contentHash, HistoryStatus.RETRY,
                Instant.now(), queueItemId, errorCode, errorMessage);
    }

    /**
     * 같은 미디어의 결과를 최신 상태로 갱신한다.
     *
     * <p><b>왜 새 행을 넣지 않는가</b> — POL-01(모든 실패 경로를 누락 없이 기록)과
     * AGG-01 불변식 1(해시는 미디어별로 유일)이 정면으로 부딪히는 지점이다.
     * 같은 영상이 실패 → 재시도 → 또 실패하면 매번 새 이력을 넣을 수 없다(유니크 위반).
     * 그래서 이력은 <b>미디어별 1행</b>을 유지하고 결과만 갱신한다.
     * 몇 번 실패했는지는 {@code queue_items.retry_count} 가, 각 시도의 상세는 로그가 담당한다.
     *
     * @param errorMessage 반드시 마스킹을 거친 문자열 (POL-05)
     */
    public void updateOutcome(HistoryStatus newStatus, String errorCode, String errorMessage) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus 는 필수입니다");
        }
        if (newStatus != HistoryStatus.SUCCESS && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("실패·재시도 상태에는 errorCode 가 필요합니다");
        }

        // ── 이미 게시된 미디어는 실패로 되돌리지 않는다 ─────────────────────
        // 인스타그램에 한 번 올라간 사실은 나중에 다른 시도가 실패해도 사라지지 않는다.
        // 이 가드가 없으면 세 가지가 동시에 깨진다:
        //   ① 게시된 영상이 이력에서 FAILED 로 보인다 (감사 기록 손실, POL-01)
        //   ② ReelsUploadService 의 중복 차단이 SUCCESS 만 보므로 게이트가 열려
        //      같은 영상이 인스타그램에 두 번 올라간다 (story_01_6 무력화)
        //   ③ PublishingLimitGuard 가 SUCCESS 수로 한도를 세므로 과소 계수되어
        //      인스타그램 일일 한도를 넘겨 호출한다
        // 개별 시도의 실패 사실은 PublishAttempt(append-only)에 그대로 남으므로 정보가 사라지지 않는다.
        if (this.status == HistoryStatus.SUCCESS && newStatus != HistoryStatus.SUCCESS) {
            throw new AlreadyPublishedException(
                    "이미 게시 성공한 미디어의 이력을 " + newStatus + " 로 되돌릴 수 없습니다 (historyId=" + id + ")");
        }

        this.status = newStatus;
        this.recordedAt = Instant.now();
        if (newStatus == HistoryStatus.SUCCESS) {
            // 성공했으면 이전 실패 흔적을 남겨 두지 않는다 — 현재 상태를 나타내는 행이므로
            this.errorCode = null;
            this.errorMessage = null;
        } else {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }

    /** 이 이력이 특정 예약 큐 항목에서 비롯됐음을 연결한다 (직접 업로드였다가 예약으로 재시도된 경우 등). */
    public void linkQueueItem(UUID queueItemId) {
        this.queueItemId = queueItemId;
    }

    /** 이미 게시 성공한 미디어인지. 중복 게시 차단의 판단 근거다. */
    public boolean isPublished() {
        return status == HistoryStatus.SUCCESS;
    }

    /**
     * 이미 게시 성공한 미디어의 이력을 실패로 되돌리려 할 때 발생한다.
     *
     * <p>{@link IllegalArgumentException} 이 아닌 별도 타입인 이유: 호출자가
     * "잘못된 입력"과 구분해 처리해야 한다 — 이 상황은 입력 오류가 아니라
     * "그 미디어는 이미 게시됐다"는 정상적인 도메인 사실이다.
     */
    public static class AlreadyPublishedException extends IllegalStateException {
        public AlreadyPublishedException(String message) {
            super(message);
        }
    }

    private static void validateHash(String contentHash) {
        if (contentHash == null || contentHash.length() != CONTENT_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "contentHash 는 " + CONTENT_HASH_LENGTH + "자 SHA-256 hex 여야 합니다");
        }
        if (!contentHash.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            // 소문자 hex 만 허용한다 — 대소문자가 섞이면 같은 해시가 다른 값으로 저장되어
            // 중복 방지(불변식 1)가 뚫린다
            throw new IllegalArgumentException("contentHash 는 소문자 16진수여야 합니다");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public HistoryStatus getStatus() {
        return status;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public UUID getQueueItemId() {
        return queueItemId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /** errorMessage 를 로그에 재출력하지 않는다 (이미 마스킹됐더라도 중복 노출을 줄인다). */
    @Override
    public String toString() {
        return "HistoryRecord{id=" + id
                + ", status=" + status
                + ", recordedAt=" + recordedAt
                + ", queueItemId=" + queueItemId
                + ", errorCode=" + errorCode + "}";
    }
}
