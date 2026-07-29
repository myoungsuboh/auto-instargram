package com.autoinstagram.backend.common.idempotency;

import com.autoinstagram.backend.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 멱등성 키별 첫 요청 결과 (skills/backEnd/idempotency-idempotency.md 규칙 2·3).
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {

    /**
     * 규칙 3 은 TTL 을 "24시간 이상"으로 요구한다. 48시간을 쓰는 이유:
     * 하루 이상 걸리는 장애 복구 중에도 클라이언트 재시도가 중복을 만들지 않게 여유를 둔다.
     */
    public static final Duration TTL = Duration.ofHours(48);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_method", nullable = false, length = 10, updatable = false)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 200, updatable = false)
    private String requestPath;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // JPA 전용
    }

    private IdempotencyRecord(UUID id, String idempotencyKey, String requestMethod,
                              String requestPath, String requestFingerprint, Instant expiresAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.requestFingerprint = requestFingerprint;
        this.state = State.IN_PROGRESS;
        this.expiresAt = expiresAt;
    }

    /** 처리 시작 표시를 만든다 (규칙 4: 이 상태에서 중복이 도달하면 409). */
    public static IdempotencyRecord begin(String idempotencyKey, String requestMethod,
                                          String requestPath, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 는 필수입니다");
        }
        if (requestFingerprint == null || requestFingerprint.length() != 64) {
            throw new IllegalArgumentException("requestFingerprint 는 64자 SHA-256 hex 여야 합니다");
        }
        return new IdempotencyRecord(UUID.randomUUID(), idempotencyKey, requestMethod,
                requestPath, requestFingerprint, Instant.now().plus(TTL));
    }

    /** 처리 완료. 이후 같은 키의 요청에는 여기 저장된 응답을 그대로 돌려준다 (규칙 2). */
    public void complete(int responseStatus, String responseBody) {
        this.state = State.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    public boolean isInProgress() {
        return state == State.IN_PROGRESS;
    }

    /** 요청 본문이 첫 요청과 같은지. 다르면 같은 키를 다른 내용에 재사용한 것이다. */
    public boolean matchesFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public State getState() {
        return state;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** 저장된 응답 본문은 로그에 남기지 않는다 (어떤 엔드포인트의 응답이든 담길 수 있다). */
    @Override
    public String toString() {
        return "IdempotencyRecord{key=" + idempotencyKey
                + ", " + requestMethod + " " + requestPath
                + ", state=" + state
                + ", responseStatus=" + responseStatus + "}";
    }

    public enum State {
        /** 처리 중. 중복 도달 시 409 Conflict (규칙 4). */
        IN_PROGRESS,
        /** 완료. 저장된 응답을 반환 (규칙 2). */
        COMPLETED
    }
}
