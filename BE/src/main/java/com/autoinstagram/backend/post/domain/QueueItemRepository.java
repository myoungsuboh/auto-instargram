package com.autoinstagram.backend.post.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AGG-02 QueueItem 리포지터리 (2_ddd.md CTX-01 구현 체크리스트 "Repository 인터페이스").
 *
 * <p>soft-delete-audit 규칙 4(활성 레코드 기본 필터): 모든 조회 메서드 이름에
 * {@code AndDeletedAtIsNull} 을 포함한다. 이 규칙을 빼먹은 메서드를 추가하면
 * 논리 삭제된 예약이 목록에 다시 나타난다.
 */
public interface QueueItemRepository extends JpaRepository<QueueItem, UUID> {

    /** API-02 목록 조회. 최신 등록순. */
    Page<QueueItem> findByDeletedAtIsNull(Pageable pageable);

    Optional<QueueItem> findByIdAndDeletedAtIsNull(UUID id);

    /** 발행 시각이 된 대기 항목 (예약 실행 대상). */
    List<QueueItem> findByStatusAndScheduledAtLessThanEqualAndDeletedAtIsNullOrderByScheduledAtAsc(
            QueueStatus status, Instant now);

    /** 시드 멱등성 판단에 쓴다 (Verify 3: seed test queue items idempotently). */
    boolean existsByMediaPathAndDeletedAtIsNull(String mediaPath);

    /**
     * 아직 끝나지 않은(대기 중이거나 실행 중인) 같은 미디어의 작업이 있는지.
     *
     * <p>중복 게시 차단의 두 번째 축이다. 이력(history_records)만 보면
     * "첫 요청이 아직 게시 전"인 창(窓)에서 같은 영상이 다시 접수되어 두 번 올라간다.
     */
    boolean existsByMediaPathAndStatusInAndDeletedAtIsNull(String mediaPath, List<QueueStatus> statuses);

    /**
     * <b>원자적 선점(claim)</b> — PENDING 인 항목만 RUNNING 으로 바꾸고, 바꾼 행 수를 돌려준다.
     *
     * <p>3_architecture.md 는 이 서비스를 {@code Replicas: 2} 로 배포한다고 명시한다.
     * "읽어서 확인하고 → 저장"하는 방식은 두 인스턴스가 동시에 같은 PENDING 항목을 읽으면
     * 둘 다 통과해 <b>같은 영상이 인스타그램에 두 번 게시된다</b>.
     *
     * <p>이 UPDATE 는 조건절에 {@code status = PENDING} 을 포함하므로 DB 가 직렬화해 준다 —
     * 먼저 커밋한 쪽만 1을 받고, 늦은 쪽은 0을 받아 그 항목을 건너뛴다.
     * 애플리케이션 락이나 분산 락 없이 정확성을 얻는다.
     *
     * @return 1이면 이 인스턴스가 선점 성공, 0이면 다른 인스턴스가 이미 가져갔다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update QueueItem q set q.status = com.autoinstagram.backend.post.domain.QueueStatus.RUNNING "
            + "where q.id = :id "
            + "and q.status = com.autoinstagram.backend.post.domain.QueueStatus.PENDING "
            + "and q.deletedAt is null")
    int claimForRunning(@Param("id") UUID id);

    /**
     * 오래 RUNNING 상태로 멈춰 있는 항목 — 처리 중 인스턴스가 죽은 경우다.
     *
     * <p>이런 항목은 다시 집히지 않으므로(findDueItems 는 PENDING 만 본다) 영구히 게시되지 않고
     * 실패 이력도 남지 않는다. 게다가 ADR-0003 변환 때문에 API 에서는 PENDING 으로 보여
     * 운영자는 "아직 대기 중"이라고 오해한다. 그래서 회수(reclaim)가 필요하다.
     *
     * <p>{@code updatedAt} 을 기준으로 삼는다 — DB 트리거가 자동으로 채우므로 신뢰할 수 있다.
     */
    List<QueueItem> findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(QueueStatus status, Instant before);

    long countByDeletedAtIsNull();
}
