package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.util.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 미디어 콘텐츠 해시 계산 (2_ddd.md AGG-01: "SHA-256 기반 중복 업로드 방지").
 *
 * <p>{@code history_records.content_hash} 는 NOT NULL 이고 활성 레코드 한정 유니크다.
 * 즉 이력을 남기려면 언제나 해시가 있어야 한다 — 파일을 읽을 수 없는 실패 상황에서도 그렇다
 * (POL-01: 모든 실패 경로를 누락 없이 기록).
 * 그래서 파일 해시를 우선 시도하고, 불가능하면 경로 문자열 해시로 대체한다.
 *
 * <p>해시 계산 자체는 {@link Sha256} 이 담당한다 — 형식(소문자 hex 64자)이
 * 여러 곳에서 갈리면 중복 방지가 뚫리므로 변환을 한곳에 모았다.
 */
@Component
public class MediaHasher {

    private static final Logger log = LoggerFactory.getLogger(MediaHasher.class);

    /**
     * 파일 내용의 SHA-256. 파일을 읽을 수 없으면 경로 문자열 해시로 대체한다.
     *
     * <p>대체 동작을 두는 이유: 해시가 없으면 이력을 남길 수 없고, 그러면 POL-01 을 위반한다.
     * "실패를 기록조차 못 하는 것"보다 "경로 기준으로라도 기록하는 것"이 낫다.
     * 이 경우 중복 방지는 콘텐츠가 아니라 경로 기준으로 동작한다 — 그 사실을 로그로 남긴다.
     */
    public String hashMedia(String mediaPath) {
        if (mediaPath == null || mediaPath.isBlank()) {
            throw new IllegalArgumentException("mediaPath 는 필수입니다");
        }

        Path path;
        try {
            path = Path.of(mediaPath);
        } catch (InvalidPathException ex) {
            // 경로에 쓸 수 없는 문자가 있는 경우 (Windows 의 < > : " | ? * 등)
            log.debug("경로 형식이 올바르지 않아 경로 문자열 해시로 대체합니다");
            return Sha256.hex(mediaPath);
        }

        if (Files.isDirectory(path) || !Files.isReadable(path)) {
            log.info("미디어 파일을 읽을 수 없어 경로 기준 해시를 사용합니다 (중복 방지가 경로 기준으로 동작)");
            return Sha256.hex(mediaPath);
        }

        try (InputStream in = Files.newInputStream(path)) {
            return Sha256.hex(in);

        } catch (IOException ex) {
            // 규칙 1(에러를 삼키지 않는다): 원인을 남기고 대체 경로로 진행한다
            log.warn("미디어 파일 읽기 실패로 경로 기준 해시를 사용합니다: {}", ex.getClass().getSimpleName());
            return Sha256.hex(mediaPath);
        }
    }
}
