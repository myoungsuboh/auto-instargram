package com.autoinstagram.backend.auth.jwt;

import com.autoinstagram.backend.auth.domain.AccountRole;
import com.autoinstagram.backend.auth.domain.AppAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT) 발급·검증.
 *
 * <p>갱신 토큰은 JWT 가 아니라 불투명(opaque) 난수다 — {@link RefreshTokenFactory} 참고.
 * 갱신 토큰은 폐기(revoke)가 가능해야 하는데, 자체 완결적인 JWT 는 서버가 폐기할 수 없기 때문이다.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "username";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.secret());
    }

    /**
     * 설정이 보안 규칙을 만족하는지 기동 시점에 검증한다.
     *
     * <p>SKL-SECRETS-MANAGEMENT 규칙 3(시작 시 누락을 검증, fail-fast):
     * 잘못된 설정으로 조용히 떠서 운영 중에 취약해지는 것보다 아예 뜨지 않는 게 낫다.
     */
    @PostConstruct
    void validateConfiguration() {
        Duration ttl = properties.accessTokenTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("app.jwt.access-token-ttl 이 설정되지 않았습니다");
        }
        if (ttl.compareTo(JwtProperties.MAX_ACCESS_TOKEN_TTL) > 0) {
            throw new IllegalStateException(
                    "app.jwt.access-token-ttl(" + ttl + ") 이 15분을 초과합니다. "
                            + "skills/security/JWT-authn-authz.md 규칙 2 위반입니다.");
        }
        Duration refreshTtl = properties.refreshTokenTtl();
        if (refreshTtl == null || refreshTtl.compareTo(ttl) <= 0) {
            throw new IllegalStateException(
                    "app.jwt.refresh-token-ttl 은 access-token-ttl 보다 길어야 합니다");
        }
        log.info("JWT 설정 확인 완료 — access {} / refresh {} / cookie secure={} sameSite={}",
                ttl, refreshTtl, properties.cookieSecure(), properties.cookieSameSite());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수가 없습니다. .env.example 을 .env 로 복사한 뒤 값을 채우세요.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < JwtProperties.MIN_SECRET_BYTES) {
            // 길이만 알려주고 값은 절대 로그에 남기지 않는다 (POL-05)
            throw new IllegalStateException(
                    "JWT_SECRET 이 너무 짧습니다 (" + keyBytes.length + "바이트). "
                            + JwtProperties.MIN_SECRET_BYTES + "바이트 이상이 필요합니다. "
                            + "생성 예: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 액세스 토큰을 발급한다. */
    public String createAccessToken(AppAccount account) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(account.getId().toString())
                .claim(CLAIM_USERNAME, account.getUsername())
                .claim(CLAIM_ROLE, account.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Duration getAccessTokenTtl() {
        return properties.accessTokenTtl();
    }

    /**
     * 토큰을 검증하고 주체 정보를 돌려준다.
     *
     * <p>서명 불일치·만료·형식 오류를 구분하지 않고 모두 "인증 실패"로 처리한다 —
     * 어느 쪽인지 알려주면 공격자에게 정보를 준다.
     *
     * @return 유효하면 주체 정보, 아니면 {@link Optional#empty()}
     */
    public Optional<AuthenticatedUser> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID accountId = UUID.fromString(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            AccountRole role = AccountRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            Instant expiresAt = claims.getExpiration().toInstant();
            return Optional.of(new AuthenticatedUser(accountId, username, role, expiresAt));

        } catch (JwtException | IllegalArgumentException ex) {
            // 규칙 1(에러를 삼키지 않는다): 토큰 원문은 남기지 않고 실패 사실과 유형만 남긴다.
            log.debug("액세스 토큰 검증 실패: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 검증된 요청 주체.
     *
     * @param expiresAt 이 액세스 토큰의 만료 시각. 화면이 자동 갱신 시점을 정확히 잡으려면
     *                  전체 수명(항상 900초)이 아니라 <b>실제 남은 시간</b>이 필요하다
     */
    public record AuthenticatedUser(UUID accountId, String username, AccountRole role, Instant expiresAt) {

        /** 만료까지 남은 초. 이미 만료됐으면 0. */
        public long remainingSeconds() {
            long seconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(seconds, 0L);
        }
    }
}
