package com.autoinstagram.backend.auth.service;

import com.autoinstagram.backend.auth.domain.AccountRole;
import com.autoinstagram.backend.auth.domain.AppAccount;
import com.autoinstagram.backend.auth.domain.AppAccountRepository;
import com.autoinstagram.backend.auth.domain.RefreshToken;
import com.autoinstagram.backend.auth.domain.RefreshTokenRepository;
import com.autoinstagram.backend.auth.jwt.JwtProperties;
import com.autoinstagram.backend.auth.jwt.JwtTokenProvider;
import com.autoinstagram.backend.auth.jwt.RefreshTokenFactory;
import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 로그인·갱신·로그아웃.
 *
 * <p>⚠️ 1_spack.md 에 없는 기능이다. 명세의 API 5개가 모두 인증을 요구하는데 로그인 창구가 없어,
 * 사용자 확정 결정으로 추가했다 (docs/decisions/2026-07-29-dashboard-login-added.md).
 *
 * <p>적용 규칙 — skills/security/JWT-authn-authz.md
 * <ul>
 *   <li>규칙 2 — 액세스 토큰 15분 + 갱신 토큰으로 재발급. 갱신 시 <b>회전</b>(기존 폐기 후 새 발급)한다</li>
 *   <li>규칙 5 — 실패한 로그인 시도를 계정별({@link AppAccount})·IP별({@link LoginAttemptGuard})로 제한</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenFactory refreshTokenFactory;
    private final LoginAttemptGuard attemptGuard;
    private final JwtProperties jwtProperties;

    public AuthService(AppAccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenFactory refreshTokenFactory,
                       LoginAttemptGuard attemptGuard,
                       JwtProperties jwtProperties) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenFactory = refreshTokenFactory;
        this.attemptGuard = attemptGuard;
        this.jwtProperties = jwtProperties;
        // 아무도 모르는 난수를 해싱해 둔다 — 이 해시와 일치하는 비밀번호는 존재하지 않는다
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 아이디·비밀번호로 로그인한다.
     *
     * <p>실패 사유(아이디 없음 / 비밀번호 틀림 / 잠김)를 응답에서 구분해 알려주지 않는다 —
     * 구분해 주면 어떤 아이디가 존재하는지 알려주는 셈이 된다(계정 열거 공격).
     * 다만 잠긴 경우는 사용자가 기다려야 함을 알아야 하므로 429 로 구분한다.
     */
    @Transactional
    public IssuedTokens login(String username, String rawPassword, String clientIp) {
        // ── IP 단위 제한 먼저 (규칙 5) ────────────────────────────────
        if (!attemptGuard.isAllowed(clientIp)) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "IP " + clientIp + " 의 로그인 시도 한도 초과");
        }

        AppAccount account = accountRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElse(null);

        if (account == null) {
            attemptGuard.recordFailure(clientIp);
            // 존재하지 않는 아이디여도 비밀번호 검증과 비슷한 시간을 쓰게 해 타이밍 차이로
            // 아이디 존재 여부가 드러나지 않게 한다.
            passwordEncoder.matches(rawPassword, dummyHash);
            log.warn("로그인 실패 — 존재하지 않는 아이디 '{}' (ip={})", username, clientIp);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "알 수 없는 아이디: " + username);
        }

        // ── 계정 단위 잠금 (규칙 5) ───────────────────────────────────
        if (account.isLocked()) {
            attemptGuard.recordFailure(clientIp);
            log.warn("로그인 실패 — 계정 '{}' 은 {} 까지 잠김", username, account.getLockedUntil());
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "계정 잠금 상태: " + username + " (해제 " + account.getLockedUntil() + ")");
        }

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            attemptGuard.recordFailure(clientIp);
            boolean nowLocked = account.recordLoginFailure();
            accountRepository.save(account);
            log.warn("로그인 실패 — 비밀번호 불일치 '{}' (ip={}, 계정잠금={})", username, clientIp, nowLocked);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "비밀번호 불일치: " + username);
        }

        // ── 성공 ─────────────────────────────────────────────────────
        account.recordLoginSuccess();
        accountRepository.save(account);
        attemptGuard.recordSuccess(clientIp);
        log.info("로그인 성공 — '{}' (role={}, ip={})", username, account.getRole(), clientIp);

        return issueFor(account);
    }

    /**
     * 갱신 토큰으로 액세스 토큰을 재발급한다 (규칙 2).
     *
     * <p>회전(rotation)한다: 쓰인 갱신 토큰은 즉시 폐기하고 새 토큰을 함께 내려준다.
     * 회전하지 않으면 유출된 갱신 토큰을 만료까지 계속 쓸 수 있다.
     */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "갱신 토큰 쿠키가 없음");
        }

        String hash = refreshTokenFactory.hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndDeletedAtIsNull(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "갱신 토큰을 찾을 수 없음"));

        if (!stored.isUsable()) {
            // 이미 폐기된 토큰의 재사용은 탈취 신호일 수 있다.
            // 해당 계정의 모든 갱신 토큰을 폐기해 세션을 끊는다.
            int revoked = refreshTokenRepository.revokeAllForAccount(stored.getAppAccountId(), Instant.now());
            log.warn("폐기·만료된 갱신 토큰 재사용 감지 — 계정 {} 의 세션 {}건을 모두 폐기",
                    stored.getAppAccountId(), revoked);
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "사용할 수 없는 갱신 토큰");
        }

        AppAccount account = accountRepository.findByIdAndDeletedAtIsNull(stored.getAppAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "계정이 없거나 삭제됨"));

        stored.revoke();
        refreshTokenRepository.save(stored);

        log.debug("액세스 토큰 재발급 — '{}'", account.getUsername());
        return issueFor(account);
    }

    /** 로그아웃: 해당 계정의 유효한 갱신 토큰을 전부 폐기한다. */
    @Transactional
    public void logout(UUID accountId) {
        if (accountId == null) {
            return;
        }
        int revoked = refreshTokenRepository.revokeAllForAccount(accountId, Instant.now());
        log.info("로그아웃 — 계정 {} 의 갱신 토큰 {}건 폐기", accountId, revoked);
    }

    /** 현재 계정 조회 (세션 확인용). */
    @Transactional(readOnly = true)
    public AppAccount requireAccount(UUID accountId) {
        return accountRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "계정이 없거나 삭제됨"));
    }

    private IssuedTokens issueFor(AppAccount account) {
        String accessToken = tokenProvider.createAccessToken(account);
        String rawRefresh = refreshTokenFactory.generateToken();
        Duration refreshTtl = jwtProperties.refreshTokenTtl();

        refreshTokenRepository.save(RefreshToken.issue(
                account.getId(),
                refreshTokenFactory.hash(rawRefresh),
                Instant.now().plus(refreshTtl)));

        return new IssuedTokens(
                accessToken,
                rawRefresh,
                tokenProvider.getAccessTokenTtl(),
                refreshTtl,
                account.getUsername(),
                account.getRole());
    }

    /**
     * 아이디가 없을 때도 비밀번호 대조와 비슷한 시간을 쓰기 위한 더미 BCrypt 해시.
     *
     * <p>기동 시 무작위 값을 실제로 해싱해서 만든다. 손으로 쓴 상수를 쓰면 안 되는 이유:
     * BCrypt 해시 형식(솔트 22자 등)이 조금이라도 틀리면 인코더가 대조를 시작하지도 않고
     * 경고 로그와 함께 false 를 돌려준다 — 그러면 타이밍 방어가 무력화되고 로그만 더러워진다.
     *
     * <p>어떤 비밀번호와도 일치하지 않는다(입력을 모르는 난수를 해싱했으므로).
     */
    private final String dummyHash;

    /**
     * 발급된 토큰 묶음. 컨트롤러가 이걸 쿠키로 바꿔 내려준다 —
     * 응답 바디로는 절대 나가지 않는다 (SKL-AUTHN-AUTHZ 규칙 1).
     */
    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            Duration accessTtl,
            Duration refreshTtl,
            String username,
            AccountRole role
    ) {
        /** 토큰이 로그로 새지 않게 한다. */
        @Override
        public String toString() {
            return "IssuedTokens{username='" + username + "', role=" + role
                    + ", accessTtl=" + accessTtl + ", tokens=***}";
        }
    }
}
