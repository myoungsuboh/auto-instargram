package com.autoinstagram.backend.post.api;

import com.autoinstagram.backend.post.api.dto.HistoryListResponse;
import com.autoinstagram.backend.post.service.HistoryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-03 {@code GET /api/v1/history} — 게시 이력 조회 API.
 *
 * <p>1_spack.md:
 * <ul>
 *   <li>구현 Story: Story-01.4 "history.json 및 이력 스키마 도입"</li>
 *   <li>쿼리 파라미터: {@code startDate}, {@code endDate} (둘 다 선택)</li>
 *   <li>응답 200 OK: {@code {"history": []}}</li>
 *   <li>required_roles: {@code [system_operator, system_admin]}
 *       — 강제는 {@link com.autoinstagram.backend.config.SecurityConfig}</li>
 * </ul>
 *
 * <p>POL-03: 결과가 0건이어도 200 + 빈 배열을 반환한다.
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * 기간별 게시 이력을 조회한다.
     *
     * <p>날짜 형식이 잘못되면 Spring 이 {@code MethodArgumentTypeMismatchException} 을 던지고,
     * {@link com.autoinstagram.backend.common.error.GlobalExceptionHandler} 가 400 으로 변환한다
     * (1_spack.md "에러 응답 가이드": 400 = 요청 본문 검증 실패).
     */
    @GetMapping
    public HistoryListResponse list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return HistoryListResponse.from(historyService.findHistory(startDate, endDate));
    }
}
