package com.autoinstagram.backend.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 갱신 토큰 생성 및 해싱.
 *
 * <p>갱신 토큰을 JWT 로 만들지 않은 이유: JWT 는 자체 완결적이라 서버가 폐기할 수 없다.
 * 로그아웃과 회전(rotation)이 실제로 동작해야 하므로, 불투명 난수를 발급하고
 * 그 SHA-256 해시를 DB 에 저장해 대조한다.
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
     * 저장·대조용 SHA-256 해시(64자 hex).
     *
     * <p>비밀번호와 달리 BCrypt 를 쓰지 않는다: 이 값은 이미 256비트 고엔트로피 난수이므로
     * 무차별 대입이 불가능하고, 매 요청마다 대조해야 해서 의도적으로 느린 해시는 부적합하다.
     */
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("해시할 토큰이 비어 있습니다");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 모든 JVM 이 반드시 제공한다. 없으면 환경이 깨진 것이므로 삼키지 않고 즉시 실패한다.
            throw new IllegalStateException("SHA-256 을 사용할 수 없는 JVM 입니다", ex);
        }
    }
}
