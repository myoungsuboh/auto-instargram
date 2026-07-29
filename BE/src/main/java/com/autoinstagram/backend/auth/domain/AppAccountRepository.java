package com.autoinstagram.backend.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 계정 조회. soft-delete-audit 규칙 4(활성 레코드 기본 필터)에 따라
 * 모든 조회 메서드는 {@code deletedAt IS NULL} 조건을 포함한다 —
 * 논리 삭제된 계정으로 로그인되면 안 된다.
 */
public interface AppAccountRepository extends JpaRepository<AppAccount, UUID> {

    /** 활성 계정만 찾는다. 삭제된 계정은 로그인할 수 없다. */
    Optional<AppAccount> findByUsernameAndDeletedAtIsNull(String username);

    /** 시드 멱등성 판단에 쓴다 (Verify 2: seed idempotently). */
    boolean existsByUsernameAndDeletedAtIsNull(String username);

    Optional<AppAccount> findByIdAndDeletedAtIsNull(UUID id);
}
