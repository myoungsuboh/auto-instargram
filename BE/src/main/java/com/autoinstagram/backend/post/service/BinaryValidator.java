package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 릴스 바이너리의 로컬 사전 검증.
 *
 * <p>1_spack.md API-04 의 422 조건: "바이너리 검증 실패 또는 한도 초과",
 * PRD 발췌: "<b>순수 바이너리 파서 기반 로컬 사전 검증</b> 및 게시 한도 확인".
 * 즉 인스타그램에 올려 보고 실패를 받는 게 아니라, 보내기 전에 우리가 먼저 걸러야 한다.
 *
 * <p>SKL-INPUT-VALIDATION 규칙 5(파일 업로드는 다중 검증)에 따라 세 겹으로 확인한다:
 * <ol>
 *   <li>확장자 — 화이트리스트</li>
 *   <li>실제 내용(매직 바이트) — 확장자만 믿지 않는다. {@code .mp4} 로 이름만 바꾼 실행 파일을 걸러낸다</li>
 *   <li>크기 — 하한·상한</li>
 * </ol>
 *
 * <p><b>구현하지 않은 검증</b>: 재생 길이·코덱·해상도는 MP4 atom 트리를 완전히 파싱해야 알 수 있고,
 * 명세가 검증 항목을 열거하지 않았다. 지어내지 않고 남겨 둔다 — 이 항목들은 인스타그램이 거부하면
 * 그 오류가 실패 이력(POL-01)에 기록된다.
 */
@Component
public class BinaryValidator {

    private static final Logger log = LoggerFactory.getLogger(BinaryValidator.class);

    /**
     * ISO Base Media File Format(MP4/MOV) 은 오프셋 4~8 에 'ftyp' 박스 타입이 온다.
     * 이 4바이트가 이 계열 파일의 사실상 식별자다.
     */
    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);
    private static final int FTYP_OFFSET = 4;
    private static final int HEADER_READ_BYTES = 12;

    /** 확장자 화이트리스트 (규칙 2: 허용할 값을 명시하고 나머지는 거부). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "m4v");

    private final long maxBytes;
    private final long minBytes;

    public BinaryValidator(@Value("${app.media.max-file-bytes:1073741824}") long maxBytes,
                           @Value("${app.media.min-file-bytes:1024}") long minBytes) {
        this.maxBytes = maxBytes;
        this.minBytes = minBytes;
    }

    /**
     * 파일을 검증한다. 실패는 모두 422 VALIDATION_ERROR 로 올린다 (명세의 에러 표).
     *
     * @param path {@link MediaPathValidator} 를 이미 통과한 경로여야 한다
     */
    public void validate(Path path) {
        long size;
        try {
            size = Files.size(path);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "파일 크기를 확인할 수 없습니다: " + path, ex);
        }

        // ── 1. 크기 ────────────────────────────────────────────────────
        if (size < minBytes) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "파일이 너무 작습니다 (" + size + " 바이트, 최소 " + minBytes + ")");
        }
        if (size > maxBytes) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "파일이 너무 큽니다 (" + size + " 바이트, 최대 " + maxBytes + ")");
        }

        // ── 2. 확장자 화이트리스트 ─────────────────────────────────────
        String fileName = path.getFileName().toString();
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "허용되지 않는 확장자입니다: '" + extension + "' (허용: " + ALLOWED_EXTENSIONS + ")");
        }

        // ── 3. 실제 내용 (매직 바이트) ─────────────────────────────────
        // 확장자는 누구나 바꿀 수 있다. 내용이 진짜 영상인지 확인한다.
        if (!hasIsoMediaSignature(path)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "파일 내용이 MP4/MOV 형식이 아닙니다 (확장자만 바뀐 파일일 수 있습니다)");
        }

        log.info("바이너리 사전 검증 통과 — {} ({} 바이트)", fileName, size);
    }

    /** 오프셋 4~8 이 'ftyp' 인지 확인한다. */
    private boolean hasIsoMediaSignature(Path path) {
        byte[] header = new byte[HEADER_READ_BYTES];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.readNBytes(header, 0, HEADER_READ_BYTES);
            if (read < HEADER_READ_BYTES) {
                return false;
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "파일 헤더를 읽을 수 없습니다: " + path, ex);
        }
        for (int i = 0; i < FTYP.length; i++) {
            if (header[FTYP_OFFSET + i] != FTYP[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        // 비교는 소문자로 통일한다 — '.MP4' 가 거부되면 안 된다
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
