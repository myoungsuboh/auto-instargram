package com.autoinstagram.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SKL-INPUT-VALIDATION 규칙 6(Path Traversal 차단) 검증.
 *
 * <p>{@code mediaPath}/{@code binaryPath} 는 사용자 입력이 그대로 파일 경로가 되는 자리다 —
 * 이 프로젝트에서 가장 위험한 입력이므로 공격 시나리오를 직접 넣어 막히는지 확인한다.
 */
class MediaPathValidatorTest {

    @TempDir
    Path tempDir;

    private MediaPathValidator validator;
    private Path baseDir;

    @BeforeEach
    void setUp() {
        baseDir = tempDir.resolve("media");
        validator = new MediaPathValidator(baseDir.toString());
        validator.ensureBaseDirExists();
    }

    @Test
    @DisplayName("허용 디렉터리 안의 상대 경로는 통과한다")
    void allowsRelativePathInsideBase() {
        Path result = validator.validate("reel.mp4");

        // 문자열로 비교하는 이유: AssertJ 의 Path.startsWith 는 내부적으로 toRealPath 를 호출해
        // 파일이 실제로 존재해야 한다. 여기서 검증하려는 것은 "경로 계산 결과"이지
        // 파일 존재 여부가 아니다 (존재 검사는 validateExistingFile 의 몫이다).
        assertThat(result.toString()).startsWith(validator.getAllowedBaseDir().toString());
        assertThat(result.getFileName().toString()).isEqualTo("reel.mp4");
    }

    @Test
    @DisplayName("하위 폴더도 허용한다")
    void allowsSubdirectory() {
        Path result = validator.validate("2026/07/reel.mp4");

        assertThat(result.toString()).startsWith(validator.getAllowedBaseDir().toString());
        assertThat(result.getFileName().toString()).isEqualTo("reel.mp4");
    }

    @Test
    @DisplayName("상위로 올라가는 경로(..)를 차단한다")
    void blocksParentTraversal() {
        assertThatThrownBy(() -> validator.validate("../secret.txt"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("여러 단계 상위로 올라가는 고전적 공격을 차단한다")
    void blocksDeepTraversal() {
        for (String attack : new String[]{
                "../../../../etc/passwd",
                "../../../../../../windows/system32/config/SAM",
                "subdir/../../outside.txt",
                "./../../escape.mp4"
        }) {
            assertThatThrownBy(() -> validator.validate(attack))
                    .as("공격 경로가 통과했다: %s", attack)
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("허용 디렉터리 밖을 가리키는 절대 경로를 차단한다")
    void blocksAbsolutePathOutsideBase() throws IOException {
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "비밀");

        assertThatThrownBy(() -> validator.validate(outside.toString()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("허용된 미디어 디렉터리 밖");
    }

    @Test
    @DisplayName("허용 디렉터리 안의 절대 경로는 통과한다")
    void allowsAbsolutePathInsideBase() {
        Path inside = validator.getAllowedBaseDir().resolve("ok.mp4");

        assertThat(validator.validate(inside.toString()))
                .isEqualTo(inside);
    }

    @Test
    @DisplayName("빈 경로를 거부한다")
    void rejectsBlankPath() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("거부 메시지가 서버의 디렉터리 구조를 사용자에게 노출하지 않는다")
    void errorResponseDoesNotLeakServerPaths() {
        // ApiException 의 logDetail 에는 경로가 있어도 되지만(로그용),
        // 사용자에게 나가는 errorCode 의 메시지에는 없어야 한다 (OWASP #4)
        ApiException thrown = (ApiException) org.assertj.core.api.Assertions
                .catchThrowable(() -> validator.validate("../../etc/passwd"));

        assertThat(thrown.getErrorCode().getMessage())
                .doesNotContain("etc")
                .doesNotContain(baseDir.toString());
    }

    // ── validateExistingFile ────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 읽기 가능한 파일은 통과한다")
    void acceptsExistingReadableFile() throws IOException {
        Path file = validator.getAllowedBaseDir().resolve("exists.mp4");
        Files.writeString(file, "내용");

        assertThat(validator.validateExistingFile("exists.mp4")).isEqualTo(file);
    }

    @Test
    @DisplayName("없는 파일은 거부한다")
    void rejectsMissingFile() {
        assertThatThrownBy(() -> validator.validateExistingFile("nope.mp4"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("존재하지 않습니다");
    }

    @Test
    @DisplayName("디렉터리를 파일로 넘기면 거부한다")
    void rejectsDirectory() throws IOException {
        Files.createDirectories(validator.getAllowedBaseDir().resolve("folder"));

        assertThatThrownBy(() -> validator.validateExistingFile("folder"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("디렉터리");
    }

    @Test
    @DisplayName("허용 디렉터리가 없으면 자동으로 만든다")
    void createsBaseDirIfMissing() {
        Path fresh = tempDir.resolve("brand-new-dir");
        MediaPathValidator freshValidator = new MediaPathValidator(fresh.toString());

        freshValidator.ensureBaseDirExists();

        assertThat(Files.isDirectory(fresh)).isTrue();
    }

    @Test
    @DisplayName("설정이 비어 있으면 기동 시점에 거부한다 (fail-fast)")
    void rejectsBlankConfiguration() {
        assertThatThrownBy(() -> new MediaPathValidator(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MEDIA_BASE_DIR");
    }
}
