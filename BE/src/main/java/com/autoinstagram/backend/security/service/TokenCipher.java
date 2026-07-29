package com.autoinstagram.backend.security.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰의 저장 시 암호화 (POL-05, 1_spack.md ENT-03 "암호화된 액세스 토큰").
 *
 * <p>AES-256-GCM 을 쓴다. GCM 은 기밀성뿐 아니라 무결성까지 보장하므로,
 * DB 의 암호문이 변조되면 복호화가 실패해 조용히 잘못된 값을 쓰는 일이 없다.
 *
 * <p>저장 형식: {@code base64(IV || ciphertext || tag)}
 * IV 는 매번 새로 만들어 앞에 붙인다 — 같은 토큰을 두 번 암호화해도 암호문이 달라야 한다
 * (IV 재사용은 GCM 에서 치명적 취약점이다).
 *
 * <p>키는 코드에 없고 환경변수 {@code CREDENTIAL_ENCRYPTION_KEY} 에서 주입된다
 * (SKL-SECRETS-MANAGEMENT 규칙 1).
 */
@Component
public class TokenCipher {

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 권장 IV 길이 (96비트). */
    private static final int IV_BYTES = 12;

    /** GCM 인증 태그 길이 (비트). */
    private static final int TAG_BITS = 128;

    /** AES-256 이 요구하는 키 길이. */
    private static final int KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey key;

    public TokenCipher(@Value("${app.credential.encryption-key:}") String base64Key) {
        this.key = buildKey(base64Key);
    }

    @PostConstruct
    void logReady() {
        // 키 값도, 길이 외의 어떤 정보도 남기지 않는다
        log.info("토큰 저장 암호화 준비 완료 (AES-256-GCM)");
    }

    private static SecretKey buildKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "CREDENTIAL_ENCRYPTION_KEY 환경변수가 없습니다. "
                            + ".env.example 을 .env 로 복사한 뒤 값을 채우세요. "
                            + "생성 예: openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "CREDENTIAL_ENCRYPTION_KEY 가 올바른 base64 가 아닙니다. "
                            + "생성 예: openssl rand -base64 32", ex);
        }
        if (raw.length != KEY_BYTES) {
            // 길이만 알려준다 — 키 값 자체는 절대 로그·예외에 넣지 않는다
            throw new IllegalStateException(
                    "CREDENTIAL_ENCRYPTION_KEY 길이가 " + raw.length + "바이트입니다. "
                            + "AES-256 은 정확히 " + KEY_BYTES + "바이트가 필요합니다. "
                            + "생성 예: openssl rand -base64 32");
        }
        return new SecretKeySpec(raw, ALGORITHM);
    }

    /** 평문 토큰을 암호화한다. 결과만 저장하고 평문은 즉시 버린다. */
    public String encrypt(String plainToken) {
        if (plainToken == null || plainToken.isEmpty()) {
            throw new IllegalArgumentException("암호화할 토큰이 비어 있습니다");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);

        } catch (GeneralSecurityException ex) {
            // 예외 메시지에 평문이 섞이지 않도록 원인만 감싸 올린다 (규칙 1: 삼키지 않는다)
            throw new IllegalStateException("토큰 암호화에 실패했습니다", ex);
        }
    }

    /** 암호문을 복호화한다. 변조되었으면 예외가 난다(GCM 무결성 검증). */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalArgumentException("복호화할 암호문이 비어 있습니다");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_BYTES) {
                throw new IllegalStateException("암호문이 손상되었습니다 (길이 부족)");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("토큰 복호화에 실패했습니다 (키 불일치 또는 변조)", ex);
        }
    }
}
