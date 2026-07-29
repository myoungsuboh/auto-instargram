package com.autoinstagram.backend.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** 활성(논리 삭제되지 않은) 토큰을 해시로 찾는다. */
    Optional<RefreshToken> findByTokenHashAndDeletedAtIsNull(String tokenHash);

    List<RefreshToken> findByAppAccountIdAndRevokedAtIsNullAndDeletedAtIsNull(UUID appAccountId);

    /**
     * 로그아웃: 해당 계정의 유효한 갱신 토큰을 모두 폐기한다.
     * 한 건씩 로드해 저장하면 세션 수만큼 쿼리가 늘어나므로 벌크 갱신으로 처리한다.
     * 벌크 UPDATE 는 updated_at 을 건드리지 않지만, DB 트리거가 자동으로 채운다(V1 마이그레이션).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.appAccountId = :accountId and t.revokedAt is null and t.deletedAt is null")
    int revokeAllForAccount(@Param("accountId") UUID accountId, @Param("now") Instant now);
}
