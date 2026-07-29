package com.autoinstagram.backend.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 해시 계산의 단일 소유 지점.
 *
 * <p>이 프로젝트는 세 곳에서 SHA-256 hex 를 쓴다:
 * 갱신 토큰 해시({@code RefreshTokenFactory}), 미디어 콘텐츠 해시({@code MediaHasher}),
 * 멱등성 요청 지문({@code QueueController}).
 * 각자 구현하면 <b>대소문자나 패딩이 미묘하게 달라져</b> 같은 입력이 다른 해시가 되고,
 * 그러면 중복 방지와 멱등성이 조용히 뚫린다. 그래서 변환을 한곳으로 모았다.
 *
 * <p>출력은 항상 <b>소문자 hex 64자</b>다 — DB 의 CHECK 제약
 * ({@code char_length(...) = 64})과 {@code HistoryRecord} 의 소문자 검증이 이 형식을 전제한다.
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    /** 대용량 파일을 메모리에 전부 올리지 않기 위한 스트리밍 버퍼. */
    private static final int BUFFER_BYTES = 64 * 1024;

    private Sha256() {
    }

    /** 문자열(UTF-8)의 SHA-256 을 소문자 hex 64자로 돌려준다. */
    public static String hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("해시할 문자열이 null 입니다");
        }
        return toHex(newDigest().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 스트림 내용의 SHA-256. 스트림을 끝까지 읽지만 닫지는 않는다(호출자 책임).
     *
     * @throws IOException 읽기 실패 — 호출자가 대체 동작을 결정한다
     */
    public static String hex(InputStream input) throws IOException {
        try (DigestInputStream digestStream = new DigestInputStream(input, newDigest())) {
            byte[] buffer = new byte[BUFFER_BYTES];
            // 반환값을 쓰지 않는다 — DigestInputStream 이 읽는 동안 해시를 누적한다
            while (digestStream.read(buffer) != -1) {
                // 본문은 필요 없다. 해시만 누적한다
            }
            return toHex(digestStream.getMessageDigest().digest());
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 모든 JVM 이 반드시 제공한다. 없으면 환경이 깨진 것이므로 즉시 실패한다.
            throw new IllegalStateException("SHA-256 을 사용할 수 없는 JVM 입니다", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
