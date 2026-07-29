package com.autoinstagram.backend.post.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoinstagram.backend.common.error.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 1_spack.md API-04 의 "순수 바이너리 파서 기반 로컬 사전 검증" 검증.
 *
 * <p>SKL-INPUT-VALIDATION 규칙 5(파일 업로드는 확장자·내용·크기를 모두 검증) 이행 확인.
 * 핵심은 <b>확장자만 바꾼 파일이 통과하지 못한다</b>는 것이다.
 */
class BinaryValidatorTest {

    /** 최소 크기를 낮춰 테스트 파일을 작게 유지한다. */
    private static final long MIN_BYTES = 16;
    private static final long MAX_BYTES = 4096;

    @TempDir
    Path tempDir;

    private BinaryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BinaryValidator(MAX_BYTES, MIN_BYTES);
    }

    /**
     * ISO Base Media File Format 헤더를 가진 최소 파일을 만든다.
     * 오프셋 4~8 이 'ftyp' 인 것이 이 계열의 식별자다.
     */
    private Path writeMp4(String name, int totalBytes) throws IOException {
        byte[] content = new byte[totalBytes];
        // 0~4: box size (임의) / 4~8: 'ftyp' / 8~12: brand
        content[0] = 0; content[1] = 0; content[2] = 0; content[3] = 0x20;
        byte[] ftyp = "ftyp".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ftyp, 0, content, 4, 4);
        byte[] brand = "isom".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(brand, 0, content, 8, 4);

        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    @Test
    @DisplayName("정상 MP4 는 통과한다")
    void acceptsValidMp4() throws IOException {
        Path file = writeMp4("reel.mp4", 512);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(".mov / .m4v 도 허용한다")
    void acceptsMovAndM4v() throws IOException {
        assertThatCode(() -> validator.validate(writeMp4("clip.mov", 512)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(writeMp4("clip.m4v", 512)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대문자 확장자도 허용한다 (.MP4)")
    void acceptsUppercaseExtension() throws IOException {
        assertThatCode(() -> validator.validate(writeMp4("REEL.MP4", 512)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("규칙 5 핵심: 확장자만 .mp4 로 바꾼 파일은 거부한다")
    void rejectsFakeExtension() throws IOException {
        // 실행 파일 헤더(MZ)에 .mp4 이름만 붙인 경우 — 확장자만 믿으면 통과해 버린다
        byte[] exeHeader = new byte[512];
        exeHeader[0] = 'M';
        exeHeader[1] = 'Z';
        Path fake = tempDir.resolve("malware.mp4");
        Files.write(fake, exeHeader);

        assertThatThrownBy(() -> validator.validate(fake))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MP4/MOV 형식이 아닙니다");
    }

    @Test
    @DisplayName("허용되지 않은 확장자를 거부한다")
    void rejectsDisallowedExtension() throws IOException {
        Path file = writeMp4("reel.exe", 512);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("허용되지 않는 확장자");
    }

    @Test
    @DisplayName("확장자가 없는 파일을 거부한다")
    void rejectsNoExtension() throws IOException {
        Path file = writeMp4("noextension", 512);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("확장자");
    }

    @Test
    @DisplayName("너무 큰 파일을 거부한다 (한도 초과)")
    void rejectsTooLarge() throws IOException {
        Path file = writeMp4("huge.mp4", (int) MAX_BYTES + 1);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("너무 큽니다");
    }

    @Test
    @DisplayName("너무 작은 파일을 거부한다 (빈 파일·손상 파일)")
    void rejectsTooSmall() throws IOException {
        Path file = tempDir.resolve("tiny.mp4");
        Files.write(file, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("너무 작습니다");
    }

    @Test
    @DisplayName("헤더를 읽을 만큼도 안 되는 파일을 거부한다")
    void rejectsFileShorterThanHeader() throws IOException {
        // 크기 하한은 넘지만 12바이트 헤더를 채우지 못하는 경우
        Path file = tempDir.resolve("short.mp4");
        Files.write(file, new byte[(int) MIN_BYTES + 1]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class);
    }
}
