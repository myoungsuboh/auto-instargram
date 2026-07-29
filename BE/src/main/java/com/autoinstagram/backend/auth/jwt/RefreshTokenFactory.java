package com.autoinstagram.backend.auth.jwt;

import com.autoinstagram.backend.common.util.Sha256;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 갱신 토큰 생성 및 해싱.
 *
 * <p>갱신 토큰을 JWT 로 만들지 않은 이유는 ADR-0007 에 있다 — 요약하면 JWT 는 자체 완결적이라
 * 서버가 폐기할 수 없고, 그러면 로그아웃과 회전(rotation)이 실제로 동작하지 않는다.
 */
@Component
public class RefreshTokenFactory {

    /** 256비트. 무작위 추측이 현실적으로 불가능한 길이. */
    private static final int TOKEN_BYTES = 32;

    /** 예측 가능한 Random 은 인증 토큰에 쓰면 안 된다. */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 새 갱신 토큰 원문을 만든다. 이 값은 쿠키로만 나가고 저장하지 않는다. */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 저장·대조용 SHA-256 해시(소문자 hex 64자).
     *
     * <p>비밀번호와 달리 BCrypt 를 쓰지 않는다: 이 값은 이미 256비트 고엔트로피 난수이므로
     * 무차별 대입이 불가능하고, 매 요청마다 대조해야 해서 의도적으로 느린 해시는 부적합하다
     * (POL-04 의 3초 예산을 깎는다).
     */
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("해시할 토큰이 비어 있습니다");
        }
        return Sha256.hex(rawToken);
    }
}
