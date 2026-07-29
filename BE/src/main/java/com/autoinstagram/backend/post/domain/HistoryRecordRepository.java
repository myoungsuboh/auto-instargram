package com.autoinstagram.backend.post.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AGG-01 HistoryRecord 리포지터리 (2_ddd.md CTX-01 구현 체크리스트 "Repository 인터페이스").
 *
 * <p>soft-delete-audit 규칙 4: 모든 조회는 {@code deletedAt IS NULL} 조건을 포함한다.
 */
public interface HistoryRecordRepository extends JpaRepository<HistoryRecord, UUID> {

    /**
     * API-03 기간 조회. startDate/endDate 가 모두 선택 항목이므로
     * 서비스가 기본 범위를 채워 넣고 항상 이 메서드를 쓴다 (분기별 메서드를 만들지 않는다).
     */
    List<HistoryRecord> findByRecordedAtBetweenAndDeletedAtIsNullOrderByRecordedAtDesc(
            Instant from, Instant to);

    /**
     * AGG-01 불변식 1(해시는 미디어별로 유일) 사전 검사.
     *
     * <p>이 검사만으로 중복이 완전히 막히지는 않는다 — 동시 요청 두 건이 모두 통과할 수 있다.
     * 최종 방어선은 DB 의 partial unique index 다. 이 메서드는 "친절한 에러 메시지"를 위한 것이다.
     */
    boolean existsByContentHashAndDeletedAtIsNull(String contentHash);

    Optional<HistoryRecord> findByContentHashAndDeletedAtIsNull(String contentHash);

    Optional<HistoryRecord> findByIdAndDeletedAtIsNull(UUID id);

    long countByDeletedAtIsNull();
}
