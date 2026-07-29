package com.autoinstagram.backend.security.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.security.domain.SecurityCredential;
import com.autoinstagram.backend.security.domain.SecurityCredentialRepository;
import com.autoinstagram.backend.security.domain.event.TokenRefreshed;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CTX-02 인스타그램 보안 관리 컨텍스트의 <b>Domain Service</b>
 * (2_ddd.md §3 구현 체크리스트 "Domain Service 클래스").
 *
 * <p>책임: AGG-03 {@link SecurityCredential} 의 수명주기 관리와 EVT-03 발행.
 *
 * <p>POL-05(토큰 전문 노출률 0%)를 지키는 방식:
 * <ul>
 *   <li>평문 토큰은 이 클래스의 지역 변수로만 존재하고 필드·로그·이벤트에 담기지 않는다</li>
 *   <li>저장 직전 {@link TokenCipher} 로 암호화한다</li>
 *   <li>발행되는 {@link TokenRefreshed} 이벤트에도 토큰이 없다</li>
 * </ul>
 */
@Service
public class SecurityCredentialService {

    private static final Logger log = LoggerFactory.getLogger(SecurityCredentialService.class);

    private final SecurityCredentialRepository repository;
    private final InstagramGraphClient graphClient;
    private final TokenCipher tokenCipher;
    private final ApplicationEventPublisher eventPublisher;

    public SecurityCredentialService(SecurityCredentialRepository repository,
                                     InstagramGraphClient graphClient,
                                     TokenCipher tokenCipher,
                                     ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.graphClient = graphClient;
        this.tokenCipher = tokenCipher;
        this.eventPublisher = eventPublisher;
    }

    /**
     * API-05 {@code POST /api/v1/tokens/refresh} 의 본체.
     * 단기 토큰을 장기 토큰으로 교환하고 암호화해 저장한 뒤 EVT-03 을 발행한다.
     *
     * @param shortLivedToken 1_spack.md API-05 요청 본문의 {@code shortLivedToken}
     * @return 갱신 결과 (평문 토큰 포함 — 호출자가 응답으로 내보낸 뒤 버린다)
     */
    @Transactional
    public RefreshResult refreshAccessToken(String shortLivedToken) {
        // 외부 교환 (실패 시 ApiException 으로 502/422 가 올라간다)
        InstagramGraphClient.ExchangedToken exchanged =
                graphClient.exchangeForLongLivedToken(shortLivedToken);

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(exchanged.expiresInSeconds());

        // AGG-03 불변식 2(expiresAt > issuedAt)는 issue() 가 검증한다.
        // 외부 API 가 이상한 값을 주면 여기서 걸린다.
        SecurityCredential credential;
        try {
            credential = SecurityCredential.issue(
                    tokenCipher.encrypt(exchanged.accessToken()),
                    issuedAt,
                    expiresAt);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_TOKEN,
                    "교환된 토큰이 도메인 규칙을 위반함: " + ex.getMessage(), ex);
        }

        // 이전 자격 증명은 논리 삭제하지 않고 남긴다 —
        // 감사 추적(어떤 토큰이 언제까지 유효했는지)을 보존해야 하기 때문이다.
        // 현재 유효한 것은 issued_at 이 가장 최근인 행이다.
        SecurityCredential saved = repository.save(credential);

        // EVT-03 발행. payload 에 토큰은 없다 (POL-05)
        eventPublisher.publishEvent(new TokenRefreshed(saved.getId(), expiresAt, issuedAt));

        log.info("자격 증명 갱신 완료 — credentialId={}, 만료 {}", saved.getId(), expiresAt);

        return new RefreshResult(exchanged.accessToken(), saved.remainingSeconds(), saved.getId());
    }

    /** 현재 유효한 자격 증명 (없으면 empty). */
    @Transactional(readOnly = true)
    public Optional<SecurityCredential> findCurrent() {
        return repository.findFirstByDeletedAtIsNullOrderByIssuedAtDesc();
    }

    /**
     * 현재 유효한 평문 액세스 토큰. 인스타그램에 실제로 게시할 때 쓴다(Phase 3).
     *
     * <p>반환값을 로그에 남기거나 필드에 보관하면 POL-05 위반이다 — 호출 즉시 사용하고 버려야 한다.
     */
    @Transactional(readOnly = true)
    public Optional<String> findCurrentPlainToken() {
        return repository.findFirstByDeletedAtIsNullOrderByIssuedAtDesc()
                .filter(credential -> !credential.isExpired())
                .map(credential -> tokenCipher.decrypt(credential.getTokenEncrypted()));
    }

    /**
     * 갱신 결과.
     *
     * @param accessToken      평문 장기 토큰 (API-05 응답의 {@code accessToken})
     * @param expiresInSeconds 만료까지 남은 초 (API-05 응답의 {@code expiresIn})
     * @param credentialId     저장된 자격 증명 식별자
     */
    public record RefreshResult(String accessToken, long expiresInSeconds, java.util.UUID credentialId) {
        /** 토큰이 로그로 새지 않게 한다 (POL-05). */
        @Override
        public String toString() {
            return "RefreshResult{credentialId=" + credentialId
                    + ", expiresInSeconds=" + expiresInSeconds + ", accessToken=***}";
        }
    }
}
