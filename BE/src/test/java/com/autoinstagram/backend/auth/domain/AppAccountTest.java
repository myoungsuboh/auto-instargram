package com.autoinstagram.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 계정 잠금 동작 검증 — skills/security/JWT-authn-authz.md 규칙 5
 * ("실패한 로그인 시도를 계정별·IP별로 제한하고, 임계치 초과 시 계정 잠금을 적용한다").
 */
class AppAccountTest {

    private static final String HASH = "$2a$12$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQ";

    private AppAccount newAccount() {
        return AppAccount.create("operator", HASH, AccountRole.SYSTEM_OPERATOR);
    }

    @Test
    @DisplayName("새 계정은 잠기지 않은 상태로 시작한다")
    void startsUnlocked() {
        AppAccount account = newAccount();

        assertThat(account.isLocked()).isFalse();
        assertThat(account.getFailedLoginCount()).isZero();
        assertThat(account.getId()).isNotNull();
    }

    @Test
    @DisplayName("규칙 5: 임계치 미만의 실패는 잠그지 않는다")
    void doesNotLockBelowThreshold() {
        AppAccount account = newAccount();

        for (int i = 1; i < AppAccount.MAX_FAILED_ATTEMPTS; i++) {
            boolean locked = account.recordLoginFailure();
            assertThat(locked).as("%d회 실패", i).isFalse();
            assertThat(account.isLocked()).isFalse();
        }
        assertThat(account.getFailedLoginCount()).isEqualTo(AppAccount.MAX_FAILED_ATTEMPTS - 1);
    }

    @Test
    @DisplayName("규칙 5: 임계치에 도달하면 계정을 잠근다")
    void locksAtThreshold() {
        AppAccount account = newAccount();

        boolean lockedAt = false;
        for (int i = 0; i < AppAccount.MAX_FAILED_ATTEMPTS; i++) {
            lockedAt = account.recordLoginFailure();
        }

        assertThat(lockedAt).isTrue();
        assertThat(account.isLocked()).isTrue();
        assertThat(account.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("잠근 뒤 실패 카운터를 초기화한다 (해제 직후 1회 실패로 즉시 재잠금되면 안 된다)")
    void resetsCounterAfterLocking() {
        AppAccount account = newAccount();

        for (int i = 0; i < AppAccount.MAX_FAILED_ATTEMPTS; i++) {
            account.recordLoginFailure();
        }

        // 잠금은 유지되지만 카운터는 0 이어야 한다
        assertThat(account.isLocked()).isTrue();
        assertThat(account.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("로그인 성공은 실패 카운터와 잠금을 모두 해제한다")
    void successClearsFailuresAndLock() {
        AppAccount account = newAccount();
        account.recordLoginFailure();
        account.recordLoginFailure();

        account.recordLoginSuccess();

        assertThat(account.getFailedLoginCount()).isZero();
        assertThat(account.getLockedUntil()).isNull();
        assertThat(account.isLocked()).isFalse();
        assertThat(account.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("toString 에 비밀번호 해시가 들어가지 않는다 (OWASP #4)")
    void toStringHidesPasswordHash() {
        AppAccount account = newAccount();

        assertThat(account.toString()).doesNotContain(HASH);
        assertThat(account.toString()).contains("operator");
    }

    @Test
    @DisplayName("필수 값이 없으면 생성을 거부한다 (fail-closed)")
    void rejectsMissingFields() {
        assertThatThrownBy(() -> AppAccount.create(" ", HASH, AccountRole.SYSTEM_ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppAccount.create("admin", " ", AccountRole.SYSTEM_ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppAccount.create("admin", HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("권한 표기가 명세(1_spack.md)와 Spring Security 양쪽에 맞다")
    void mapsRoleNames() {
        assertThat(AccountRole.SYSTEM_ADMIN.getSpecName()).isEqualTo("system_admin");
        assertThat(AccountRole.SYSTEM_OPERATOR.getSpecName()).isEqualTo("system_operator");
        assertThat(AccountRole.SYSTEM_ADMIN.getAuthority()).isEqualTo("ROLE_SYSTEM_ADMIN");
    }
}
