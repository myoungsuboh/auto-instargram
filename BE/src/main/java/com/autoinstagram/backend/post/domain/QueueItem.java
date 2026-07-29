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
 * ENT-02 / AGG-02 — 예약 발행 큐 항목 애그리거트 루트.
 *
 * <p>2_ddd.md AGG-02 도메인 규칙(불변식) 2개를 이 클래스가 책임진다:
 * <ol>
 *   <li>{@code queue status in {PENDING, RUNNING, COMPLETED, FAILED}}
 *       → {@link QueueStatus} enum + DB CHECK {@code ck_queue_items_status}</li>
 *   <li>{@code retryCount >= 0}
 *       → 상태 전이 메서드에서만 증가시키고, DB CHECK {@code ck_queue_items_retry_count} 로 이중 강제</li>
 * </ol>
 *
 * <p>상태를 setter 로 열지 않고 의미 있는 전이 메서드({@link #markRunning()} 등)만 공개한다 —
 * 임의 전이를 막아 불변식이 깨질 경로를 없앤다.
 */
@Entity
@Table(name = "queue_items")
public class QueueItem extends BaseEntity {

    /** 1_spack.md API-01: mediaPath len<=255 */
    public static final int MAX_MEDIA_PATH_LENGTH = 255;

    /** 1_spack.md API-01: caption len<=2200 (인스타그램 캡션 한도) */
    public static final int MAX_CAPTION_LENGTH = 2200;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "media_path", nullable = false, length = MAX_MEDIA_PATH_LENGTH)
    private String mediaPath;

    @Column(name = "caption", length = MAX_CAPTION_LENGTH)
    private String caption;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QueueStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    protected QueueItem() {
        // JPA 전용
    }

    private QueueItem(UUID id, String mediaPath, String caption, Instant scheduledAt) {
        this.id = id;
        this.mediaPath = mediaPath;
        this.caption = caption;
        this.scheduledAt = scheduledAt;
        this.status = QueueStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * 새 예약을 등록한다.
     *
     * <p>길이 제약을 여기서도 검사하는 이유: Bean Validation 은 API 경로에만 적용된다.
     * 시드·배치 등 다른 경로로 들어와도 불변식이 지켜져야 한다(fail-fast).
     */
    public static QueueItem schedule(String mediaPath, String caption, Instant scheduledAt) {
        if (mediaPath == null || mediaPath.isBlank()) {
            throw new IllegalArgumentException("mediaPath 는 필수입니다");
        }
        if (mediaPath.length() > MAX_MEDIA_PATH_LENGTH) {
            throw new IllegalArgumentException(
                    "mediaPath 는 " + MAX_MEDIA_PATH_LENGTH + "자를 넘을 수 없습니다");
        }
        if (caption != null && caption.length() > MAX_CAPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "caption 은 " + MAX_CAPTION_LENGTH + "자를 넘을 수 없습니다");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt 은 필수입니다");
        }
        return new QueueItem(UUID.randomUUID(), mediaPath, caption, scheduledAt);
    }

    /** 발행 시각이 되었고 아직 처리 전인지. */
    public boolean isDue(Instant now) {
        return status == QueueStatus.PENDING && !scheduledAt.isAfter(now);
    }

    /** 파이프라인 시작. PENDING 에서만 가능하다. */
    public void markRunning() {
        requireState(QueueStatus.PENDING);
        this.status = QueueStatus.RUNNING;
    }

    /** 게시 성공. RUNNING 에서만 가능하다. */
    public void markCompleted() {
        requireState(QueueStatus.RUNNING);
        this.status = QueueStatus.COMPLETED;
        this.lastErrorCode = null;
    }

    /**
     * 실패 처리. EVT-01 {@code QueueItemFailed} 발행의 근거가 된다.
     *
     * <p>재시도 횟수를 여기서만 증가시킨다 — 불변식 2({@code retryCount >= 0})는
     * 감소 경로를 아예 만들지 않음으로써 보장한다.
     *
     * @param errorCode 실패 원인 코드 (EVT-01 payload 의 errorCode)
     */
    public void markFailed(String errorCode) {
        if (status.isTerminal() && status == QueueStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 예약은 실패로 바꿀 수 없습니다: " + id);
        }
        this.status = QueueStatus.FAILED;
        this.retryCount++;
        this.lastErrorCode = errorCode;
        this.lastFailedAt = Instant.now();
    }

    /** 실패한 예약을 다시 대기 상태로 되돌린다 (재시도). retryCount 는 유지된다. */
    public void requeueForRetry() {
        requireState(QueueStatus.FAILED);
        this.status = QueueStatus.PENDING;
    }

    /**
     * 처리 중 인스턴스가 죽어 RUNNING 으로 멈춘 항목을 대기 상태로 되돌린다.
     *
     * <p>{@link #markFailed}를 쓰지 않는 이유: 인스턴스가 죽은 것은 이 예약의 실패가 아니므로
     * 재시도 횟수를 소모시키지 않는다. 그렇게 하면 인프라 문제로 재시도 한도가 소진된다.
     */
    public void reclaimFromStalled() {
        requireState(QueueStatus.RUNNING);
        this.status = QueueStatus.PENDING;
    }

    private void requireState(QueueStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "상태 전이가 허용되지 않습니다: " + this.status + " → (기대: " + expected + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public String getCaption() {
        return caption;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    /** 캡션 전문을 로그에 흘리지 않는다 (길어지고, 사용자 콘텐츠라 로그에 남길 이유가 없다). */
    @Override
    public String toString() {
        return "QueueItem{id=" + id
                + ", status=" + status
                + ", scheduledAt=" + scheduledAt
                + ", retryCount=" + retryCount + "}";
    }
}
