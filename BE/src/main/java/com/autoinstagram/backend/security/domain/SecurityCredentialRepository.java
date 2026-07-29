package com.autoinstagram.backend.security.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AGG-03 SecurityCredential 리포지터리 (2_ddd.md CTX-02 구현 체크리스트).
 *
 * <p>soft-delete-audit 규칙 4(활성 레코드 기본 필터): 모든 조회는 {@code deletedAt IS NULL} 을 포함한다.
 */
public interface SecurityCredentialRepository extends JpaRepository<SecurityCredential, UUID> {

    /**
     * 가장 최근에 발급된 활성 자격 증명.
     * 토큰은 갱신될 때마다 새 행으로 쌓이므로(감사 추적 보존), 현재 유효한 것은 최신 발급분이다.
     */
    Optional<SecurityCredential> findFirstByDeletedAtIsNullOrderByIssuedAtDesc();

    /** 만료 임박 순 조회 (자동 갱신 대상 선별). */
    List<SecurityCredential> findByDeletedAtIsNullOrderByExpiresAtAsc(Limit limit);

    Optional<SecurityCredential> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByDeletedAtIsNull();
}
