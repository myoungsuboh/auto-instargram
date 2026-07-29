package com.autoinstagram.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * 1_spack.md 의 각 API "에러 응답 (Error cases)" 표를 그대로 코드로 옮긴 것.
 * 응답의 {@code code} 값과 메시지는 명세와 문자 단위로 일치해야 한다 — 화면과 문서가 이 값을 계약으로 삼는다.
 */
public enum ErrorCode {

    // ── 1_spack.md 에 명시된 코드 ────────────────────────────────────────
    /** 401 — JWT 누락 또는 만료 (전 API 공통) */
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),

    /** 403 — 관리자 또는 운영자 권한 없음 (API-01 등) */
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),

    /** 422 — 미디어 경로 누락 또는 잘못된 시간 형식 (API-01) */
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "잘못된 입력값입니다"),

    /** 422 — 만료되었거나 잘못된 토큰 (API-05) */
    INVALID_TOKEN(HttpStatus.UNPROCESSABLE_CONTENT, "유효하지 않은 토큰입니다"),

    // ── 1_spack.md "에러 응답 가이드" 표의 공통 항목 ──────────────────────
    /** 400 — 요청 본문 검증 실패 */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다"),

    /** 404 — 리소스 없음 (잘못된 path id 등) */
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),

    /** 422 — 비즈니스 규칙 위반 */
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_CONTENT, "요청을 처리할 수 없습니다"),

    // ── 명세 외: 로그인 기능 추가에 따른 코드 (사용자 확정 결정) ───────────
    /** 401 — 아이디 또는 비밀번호 불일치. 어느 쪽이 틀렸는지 알려주지 않는다(계정 존재 여부 노출 방지). */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다"),

    /** 429 — SKL-AUTHN-AUTHZ 규칙 5: 로그인 실패 임계치 초과 */
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요"),

    // ── 외부 의존 실패 (SKL-ERROR-HANDLING-RESILIENCE 규칙 2) ────────────
    /** 502 — 인스타그램 Graph API 호출 실패. 우리 버그가 아니라 외부 의존 실패임을 구분한다. */
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "외부 서비스와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요"),

    /** 500 — 그 밖의 서버 오류. 사용자에게 내부 정보를 노출하지 않는다. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
