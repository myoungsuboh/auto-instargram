package com.autoinstagram.backend.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 도메인 엔티티의 공통 감사 컬럼.
 *
 * <p>skills/db/soft-delete-soft-delete-audit.md 규칙 2: 생성/수정/삭제의 '언제·누가' 를
 * 표준 컬럼으로 모든 도메인 테이블에 둔다. 삭제 플래그는 불리언이 아니라
 * nullable timestamp {@code deleted_at} (NULL = 활성) 을 쓴다.
 *
 * <p>규칙 3(감사 컬럼 자동 갱신)은 두 겹으로 보장한다:
 * <ul>
 *   <li>애플리케이션 경로 — Spring Data JPA Auditing ({@code @CreatedDate} 등)</li>
 *   <li>그 밖의 모든 경로 — DB 트리거 {@code set_updated_at} (V1 마이그레이션)</li>
 * </ul>
 * JPA 를 우회하는 raw SQL 로 수정해도 updated_at 이 누락되지 않는다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** NULL = 활성 레코드. 값이 있으면 논리 삭제됨 (물리 삭제 금지). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    /** 활성 레코드인지 (soft-delete-audit 규칙 4: 조회 기본 필터의 기준). */
    public boolean isActive() {
        return deletedAt == null;
    }

    /**
     * 물리 삭제 대신 논리 삭제한다 (soft-delete-audit 규칙 1).
     * 이미 삭제된 레코드는 최초 삭제 시각·삭제자를 보존하기 위해 덮어쓰지 않는다.
     */
    public void softDelete(String actor) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actor;
        }
    }
}
