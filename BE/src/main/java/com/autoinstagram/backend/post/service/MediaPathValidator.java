package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 미디어 경로 검증 — SKL-INPUT-VALIDATION 규칙 6(Path Traversal 차단) 구현.
 *
 * <p>1_spack.md 는 {@code mediaPath}/{@code binaryPath} 를 "로컬 파일 경로" 문자열로 받는다.
 * 사용자 입력을 파일 경로로 쓰는 것은 경로 탐색 공격의 교과서적 입구다 —
 * {@code ../../../../etc/passwd} 나 {@code C:\Windows\System32\config\SAM} 같은 값이 들어올 수 있다.
 *
 * <p>규칙 6 그대로 <b>정규화 후 허용 디렉터리 안에 있는지 확인</b>한다.
 * 문자열에서 {@code ..} 를 골라 내는 블랙리스트 방식은 쓰지 않는다 —
 * 인코딩·심볼릭 링크로 우회되며, 규칙 2(화이트리스트 우선)에도 어긋난다.
 */
@Component
public class MediaPathValidator {

    private static final Logger log = LoggerFactory.getLogger(MediaPathValidator.class);

    /** 허용 디렉터리. 이 밖의 경로는 모두 거부한다. */
    private final Path allowedBaseDir;

    public MediaPathValidator(@Value("${app.media.base-dir}") String baseDir) {
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalStateException(
                    "MEDIA_BASE_DIR 이 설정되지 않았습니다. .env.example 을 참고해 값을 채우세요.");
        }
        // 심볼릭 링크까지 풀어 실제 경로로 만든다 — 링크로 허용 범위를 벗어나는 것을 막는다
        this.allowedBaseDir = toRealPath(Path.of(baseDir).toAbsolutePath().normalize());
    }

    @PostConstruct
    void ensureBaseDirExists() {
        try {
            Files.createDirectories(allowedBaseDir);
            log.info("미디어 허용 디렉터리: {}", allowedBaseDir);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "미디어 디렉터리를 만들 수 없습니다: " + allowedBaseDir, ex);
        }
    }

    /**
     * 경로를 검증하고 정규화된 절대 경로를 돌려준다.
     *
     * @param rawPath 사용자가 보낸 경로 (허용 디렉터리 기준 상대 경로 또는 그 안의 절대 경로)
     * @return 허용 디렉터리 안의 정규화된 절대 경로
     * @throws ApiException 422 — 허용 범위를 벗어나거나 형식이 잘못된 경우
     */
    public Path validate(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "미디어 경로가 비어 있음");
        }

        // ── 0. 네트워크 경로를 문자열 단계에서 즉시 거부한다 ────────────────
        // 이 검사가 파일시스템 접근보다 반드시 앞에 있어야 한다.
        // UNC 경로(\\host\share)를 파일시스템 API 에 넘기면 Windows 가 SMB 접속을 시도해
        //   ① 요청 스레드가 20초 이상 묶이고(POL-04 위반, 동시 요청으로 서비스 마비 가능)
        //   ② 공격자 호스트로 서비스 계정의 SMB 인증 정보가 전송된다.
        // 허용 디렉터리는 항상 로컬 경로이므로 UNC 는 정상 입력일 수 없다.
        rejectNetworkPath(rawPath);

        Path candidate;
        try {
            Path given = Path.of(rawPath);
            // 상대 경로면 허용 디렉터리 기준으로 해석한다.
            // 절대 경로면 그대로 두고 아래에서 허용 범위 검사를 받는다.
            candidate = given.isAbsolute() ? given : allowedBaseDir.resolve(given);
            // normalize 는 파일시스템을 건드리지 않는 순수 문자열·구조 연산이다
            candidate = candidate.toAbsolutePath().normalize();

        } catch (InvalidPathException ex) {
            // 경로에 쓸 수 없는 문자가 있는 경우 (Windows 의 < > : " | ? * 등)
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "미디어 경로 형식이 올바르지 않습니다", ex);
        }

        // ── 1. 파일시스템을 건드리기 전에 먼저 허용 범위를 확인한다 ─────────
        // 순서가 핵심이다. 예전 구현은 toRealPath 를 먼저 호출해서,
        // 범위를 벗어난 경로도 일단 접근을 시도한 뒤에야 거부했다.
        if (!candidate.startsWith(allowedBaseDir)) {
            reject(rawPath, candidate);
        }

        // ── 2. 이제 안전하다: 허용 디렉터리 안이 확인됐으므로 실제 경로를 확인한다.
        // 심볼릭 링크로 밖을 가리키는 경우를 잡기 위해 한 번 더 검사한다.
        Path resolved = toRealPath(candidate);
        if (!resolved.startsWith(allowedBaseDir)) {
            log.warn("심볼릭 링크가 허용 디렉터리 밖을 가리켜 거부: {} → {}", candidate, resolved);
            reject(rawPath, resolved);
        }

        return resolved;
    }

    /**
     * UNC·네트워크 경로 거부.
     *
     * <p>{@code Path.of} 나 {@code normalize} 는 네트워크에 접속하지 않지만
     * {@code toRealPath}·{@code Files.isReadable} 등은 접속을 시도한다.
     * 그래서 파일시스템 API 를 <b>한 번도 호출하지 않은 상태</b>에서 문자열만 보고 걸러낸다.
     */
    private static void rejectNetworkPath(String rawPath) {
        // 구분자를 통일해 \\host\share 와 //host/share 를 함께 잡는다
        String unified = rawPath.replace('\\', '/');
        if (unified.startsWith("//")) {
            log.warn("네트워크(UNC) 경로 거부 — 파일시스템 접근 전에 차단함");
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "네트워크 경로는 사용할 수 없습니다");
        }
    }

    /** 거부 처리. 어떤 경로였는지는 로그에만 남긴다 — 응답에 담으면 서버 구조가 노출된다(OWASP #4). */
    private static void reject(String rawPath, Path resolved) {
        log.warn("허용 디렉터리를 벗어난 미디어 경로 거부: 요청={} → 해석={}", rawPath, resolved);
        throw new ApiException(ErrorCode.VALIDATION_ERROR,
                "허용된 미디어 디렉터리 밖의 경로: " + resolved);
    }

    /** 허용 디렉터리 안에 있고 실제로 읽을 수 있는 파일인지까지 확인한다. */
    public Path validateExistingFile(String rawPath) {
        Path path = validate(rawPath);
        if (!Files.exists(path)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "파일이 존재하지 않습니다: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "파일이 아니라 디렉터리입니다: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "파일을 읽을 수 없습니다: " + path);
        }
        return path;
    }

    public Path getAllowedBaseDir() {
        return allowedBaseDir;
    }

    /**
     * 실제 경로로 변환한다. 존재하지 않는 경로는 {@code toRealPath} 가 실패하므로
     * 정규화된 값을 그대로 쓴다 — 아직 만들어지지 않은 파일 경로도 검증 대상이기 때문이다.
     */
    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            // 존재하지 않는 경로는 여기로 온다. 정상 흐름이므로 예외로 만들지 않는다.
            return path;
        }
    }
}
