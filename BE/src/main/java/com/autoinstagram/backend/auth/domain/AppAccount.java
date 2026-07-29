package com.autoinstagram.backend.auth.domain;

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
 * 대시보드 로그인 계정.
 *
 * <p>⚠️ 1_spack.md 에 없는 추가 엔티티다. 명세의 API 5개가 모두 {@code auth.required=true} 이면서
 * 로그인 창구가 없어, 사용자 확정 결정으로 추가했다.
 * (근거: docs/decisions/2026-07-29-dashboard-login-added.md)
 *
 * <p>SKL-AUTHN-AUTHZ 규칙 5(로그인 실패 제한)를 계정 단위로 이 엔티티가 책임진다.
 * 실패 횟수를 DB 에 두는 이유: 서버를 재시작해도 잠금이 풀리지 않아야 한다.
 */
@Entity
@Table(name = "app_accounts")
public class AppAccount extends BaseEntity {

    /** SKL-AUTHN-AUTHZ 규칙 5: 이 횟수를 연속 실패하면 계정을 잠근다. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** 잠금 지속 시간(분). 영구 잠금은 관리자 개입 없이 복구 불가라 시간 제한 잠금을 쓴다. */
    public static final int LOCK_MINUTES = 15;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** BCrypt 해시. 평문 비밀번호는 이 클래스에 존재하지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private AccountRole role;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppAccount() {
        // JPA 전용
    }

    private AppAccount(UUID id, String username, String passwordHash, AccountRole role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.failedLoginCount = 0;
    }

    /**
     * 새 계정 생성. 이미 해시된 비밀번호만 받는다 —
     * 평문을 받아 내부에서 해시하면 평문이 이 객체를 거쳐 가므로 그렇게 하지 않는다.
     */
    public static AppAccount create(String username, String passwordHash, AccountRole role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 은 필수입니다");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash 는 필수입니다");
        }
        if (role == null) {
            throw new IllegalArgumentException("role 은 필수입니다");
        }
        return new AppAccount(UUID.randomUUID(), username, passwordHash, role);
    }

    /** 지금 로그인이 잠겨 있는지 (SKL-AUTHN-AUTHZ 규칙 5). */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * 로그인 실패를 기록한다. 임계치에 도달하면 잠근다.
     *
     * @return 이번 실패로 잠금이 걸렸으면 true
     */
    public boolean recordLoginFailure() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCK_MINUTES * 60L);
            // 잠금을 건 뒤 카운터를 초기화한다. 그렇지 않으면 잠금 해제 직후 1회 실패로 즉시 재잠금된다.
            this.failedLoginCount = 0;
            return true;
        }
        return false;
    }

    /** 로그인 성공. 실패 카운터와 잠금을 모두 해제한다. */
    public void recordLoginSuccess() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountRole getRole() {
        return role;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * 비밀번호 해시를 절대 포함하지 않는다.
     * (OWASP #4 — 민감 데이터를 로그에 노출하지 않는다. 엔티티 toString 은 로그로 새는 대표 경로다)
     */
    @Override
    public String toString() {
        return "AppAccount{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}
