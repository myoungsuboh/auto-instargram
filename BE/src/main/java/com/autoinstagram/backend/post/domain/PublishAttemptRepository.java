package com.autoinstagram.backend.post.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * POL-01 감사 추적 조회 (append-only 테이블).
 *
 * <p>soft-delete-audit 규칙 4: 조회는 활성 레코드만 본다.
 */
public interface PublishAttemptRepository extends JpaRepository<PublishAttempt, UUID> {

    /** 특정 작업의 모든 시도 내역 (최신순). */
    List<PublishAttempt> findByQueueItemIdAndDeletedAtIsNullOrderByAttemptedAtDesc(UUID queueItemId);

    /** 특정 미디어의 모든 시도 내역 — HistoryRecord 가 1행으로 뭉갠 것을 여기서 펼쳐 본다. */
    List<PublishAttempt> findByContentHashAndDeletedAtIsNullOrderByAttemptedAtDesc(String contentHash);

    /** 기간별 감사 조회. */
    List<PublishAttempt> findByAttemptedAtBetweenAndDeletedAtIsNullOrderByAttemptedAtDesc(
            Instant from, Instant to);

    long countByStatusAndDeletedAtIsNull(PublishAttempt.AttemptStatus status);

    long countByDeletedAtIsNull();
}
