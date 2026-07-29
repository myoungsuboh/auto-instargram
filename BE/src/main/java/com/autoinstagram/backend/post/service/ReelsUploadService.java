package com.autoinstagram.backend.post.service;

import com.autoinstagram.backend.common.error.ApiException;
import com.autoinstagram.backend.common.error.ErrorCode;
import com.autoinstagram.backend.post.domain.HistoryRecord;
import com.autoinstagram.backend.post.domain.HistoryRecordRepository;
import com.autoinstagram.backend.post.domain.HistoryStatus;
import com.autoinstagram.backend.post.domain.QueueItem;
import com.autoinstagram.backend.post.domain.QueueItemRepository;
import com.autoinstagram.backend.post.domain.QueueStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-04 {@code POST /api/v1/reels/upload} 의 본체 — Story-06.1.
 *
 * <p>이 서비스는 <b>동기 구간만</b> 담당한다: 사전 검증 → 작업 등록 → 즉시 201 응답.
 * 실제 4단계 게시는 {@link PublishWorker} 가 백그라운드에서 수행한다.
 * 그렇게 나눈 이유는 POL-04(API 응답 3초 이내)다 — 영상 업로드와 인스타그램 인코딩은
 * 수십 초에서 수 분이 걸리므로 요청 스레드에서 기다릴 수 없다.
 * 명세가 201 + {@code status: "PROCESSING"} 을 규정한 것도 같은 그림이다 (ADR-0013).
 *
 * <p>검증 순서는 <b>비용이 싼 것부터</b> 배치했다 — 값비싼 해시 계산 전에 형식 오류를 걸러낸다.
 */
@Service
public class ReelsUploadService {

    private static final Logger log = LoggerFactory.getLogger(ReelsUploadService.class);

    private final MediaPathValidator pathValidator;
    private final BinaryValidator binaryValidator;
    private final PublishingLimitGuard limitGuard;
    private final MediaHasher mediaHasher;
    private final HistoryRecordRepository historyRepository;
    private final QueueItemRepository queueItemRepository;
    private final QueueService queueService;

    public ReelsUploadService(MediaPathValidator pathValidator,
                              BinaryValidator binaryValidator,
                              PublishingLimitGuard limitGuard,
                              MediaHasher mediaHasher,
                              HistoryRecordRepository historyRepository,
                              QueueItemRepository queueItemRepository,
                              QueueService queueService) {
        this.pathValidator = pathValidator;
        this.binaryValidator = binaryValidator;
        this.limitGuard = limitGuard;
        this.mediaHasher = mediaHasher;
        this.historyRepository = historyRepository;
        this.queueItemRepository = queueItemRepository;
        this.queueService = queueService;
    }

    /**
     * 릴스 업로드 작업을 등록한다.
     *
     * @return 등록된 작업. 이 항목의 id 가 명세의 {@code containerId} 다
     * @throws ApiException 422 — 경로·바이너리 검증 실패, 게시 한도 초과, 중복 업로드
     */
    @Transactional
    public QueueItem requestUpload(String binaryPath, String caption) {
        // ── 1. 경로 안전성 + 파일 존재 (SKL-INPUT-VALIDATION 규칙 6) ────
        Path safePath = pathValidator.validateExistingFile(binaryPath);

        // ── 2. 바이너리 사전 검증 (명세: "순수 바이너리 파서 기반 로컬 사전 검증") ──
        binaryValidator.validate(safePath);

        // ── 3. 게시 한도 (명세: "게시 한도 확인") ──────────────────────
        limitGuard.ensureWithinLimit();

        // ── 4. 중복 업로드 방지 (AGG-01 불변식 1 / story_01_6) ─────────
        // 두 축으로 막는다:
        //   ① 이미 게시 완료된 미디어인가 (이력 기준)
        //   ② 아직 게시 전이지만 이미 접수된 같은 미디어가 있는가 (진행 중 작업 기준)
        // ②가 없으면 "응답이 느려 보여 다시 눌렀다" 같은 흔한 상황에서 같은 영상이 두 번 게시된다.
        String contentHash = mediaHasher.hashMedia(safePath.toString());
        ensureNotAlreadyPublished(contentHash);
        ensureNotAlreadyQueued(safePath.toString());

        // ── 5. 작업 등록 ──────────────────────────────────────────────
        // 예약 발행과 같은 애그리거트를 쓴다 (ADR-0012). scheduledAt=now 이므로
        // 워커가 다음 주기에 바로 집어 간다.
        String storedPath = safePath.toString();
        if (storedPath.length() > QueueItem.MAX_MEDIA_PATH_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "정규화된 파일 경로가 " + QueueItem.MAX_MEDIA_PATH_LENGTH + "자를 초과합니다");
        }

        QueueItem job = queueService.register(storedPath, caption, Instant.now());
        log.info("릴스 업로드 접수 — containerId={}, file={}", job.getId(), safePath.getFileName());
        return job;
    }

    /**
     * 이미 성공적으로 게시된 미디어인지 확인한다 (SHA-256 기반 중복 업로드 방지).
     *
     * <p>실패·재시도 이력만 있는 경우는 막지 않는다 — 그건 다시 시도해야 하는 상황이다.
     * 성공 이력이 있을 때만 거부한다.
     */
    private void ensureNotAlreadyPublished(String contentHash) {
        Optional<HistoryRecord> existing =
                historyRepository.findByContentHashAndDeletedAtIsNull(contentHash);

        if (existing.isPresent() && existing.get().getStatus() == HistoryStatus.SUCCESS) {
            log.warn("중복 업로드 차단 — 이미 게시된 미디어 (historyId={})", existing.get().getId());
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "이미 게시된 영상입니다 (중복 업로드 방지). historyId=" + existing.get().getId());
        }
    }

    /**
     * 아직 게시되지 않았지만 이미 접수된 같은 미디어가 있는지 확인한다.
     *
     * <p>이력(history_records)만 보면 <b>게시 전 창(窓)</b>이 열려 있다:
     * 첫 요청이 PENDING/RUNNING 인 동안에는 성공 이력이 없으므로 두 번째 요청도 통과하고,
     * 워커가 두 항목을 차례로 게시해 같은 릴스가 두 번 올라간다.
     */
    private void ensureNotAlreadyQueued(String mediaPath) {
        boolean inFlight = queueItemRepository.existsByMediaPathAndStatusInAndDeletedAtIsNull(
                mediaPath, List.of(QueueStatus.PENDING, QueueStatus.RUNNING));

        if (inFlight) {
            log.warn("중복 업로드 차단 — 같은 영상의 작업이 이미 진행 중입니다");
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "같은 영상의 업로드가 이미 진행 중입니다. 완료를 기다려 주세요.");
        }
    }
}
