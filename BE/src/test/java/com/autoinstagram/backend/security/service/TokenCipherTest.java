package com.autoinstagram.backend.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장 시 토큰 암호화 검증 (POL-05 / ENT-03 "암호화된 액세스 토큰").
 */
class TokenCipherTest {

    /** 테스트 전용 32바이트 키. 운영 키와 무관하다. */
    private static final String TEST_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static final String PLAIN_TOKEN = "EAAGm0PX4ZCpsBAtestTokenValue1234567890";

    private TokenCipher cipher() {
        return new TokenCipher(TEST_KEY);
    }

    @Test
    @DisplayName("암호화한 뒤 복호화하면 원문이 복원된다")
    void roundTrip() {
        TokenCipher cipher = cipher();

        String encrypted = cipher.encrypt(PLAIN_TOKEN);

        assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN_TOKEN);
    }

    @Test
    @DisplayName("암호문에 평문이 남아 있지 않다")
    void cipherTextDoesNotContainPlainText() {
        String encrypted = cipher().encrypt(PLAIN_TOKEN);

        assertThat(encrypted).doesNotContain(PLAIN_TOKEN);
        assertThat(encrypted).doesNotContain("EAAGm0PX");
    }

    @Test
    @DisplayName("같은 토큰을 두 번 암호화하면 암호문이 다르다 (IV 재사용 금지)")
    void sameInputProducesDifferentCipherText() {
        TokenCipher cipher = cipher();

        String first = cipher.encrypt(PLAIN_TOKEN);
        String second = cipher.encrypt(PLAIN_TOKEN);

        // IV 를 매번 새로 만들지 않으면 여기서 같아지고, GCM 에서 그건 치명적 취약점이다
        assertThat(first).isNotEqualTo(second);
        // 그래도 둘 다 같은 원문으로 복호화되어야 한다
        assertThat(cipher.decrypt(first)).isEqualTo(PLAIN_TOKEN);
        assertThat(cipher.decrypt(second)).isEqualTo(PLAIN_TOKEN);
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화가 실패한다 (GCM 무결성 검증)")
    void detectsTampering() {
        TokenCipher cipher = cipher();
        String encrypted = cipher.encrypt(PLAIN_TOKEN);

        // 마지막 바이트를 바꿔 변조를 흉내낸다
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] = (byte) (raw[raw.length - 1] ^ 0x01);
        String tampered = Base64.getEncoder().encodeToString(raw);

        // 조용히 쓰레기 값을 돌려주지 않고 반드시 예외가 나야 한다
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화에 실패");
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void cannotDecryptWithDifferentKey() {
        String encrypted = cipher().encrypt(PLAIN_TOKEN);

        String otherKey = Base64.getEncoder()
                .encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        TokenCipher otherCipher = new TokenCipher(otherKey);

        assertThatThrownBy(() -> otherCipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 없거나 길이가 틀리면 기동 시점에 거부한다 (fail-fast)")
    void rejectsBadKeys() {
        // 미설정
        assertThatThrownBy(() -> new TokenCipher(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREDENTIAL_ENCRYPTION_KEY");

        // base64 가 아님
        assertThatThrownBy(() -> new TokenCipher("이것은 base64 가 아님!!"))
                .isInstanceOf(IllegalStateException.class);

        // 32바이트가 아님 (16바이트)
        String shortKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
        assertThatThrownBy(() -> new TokenCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("키 검증 오류 메시지에 키 값이 노출되지 않는다")
    void errorMessageDoesNotLeakKey() {
        String shortKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

        assertThatThrownBy(() -> new TokenCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortKey);
    }
}
