package com.autoinstagram.backend.common.error;

import java.time.Instant;
import java.util.List;

/**
 * 모든 에러 응답의 단일 형태. 1_spack.md 의 에러 표(Status / Code / 메시지)를 그대로 담는다.
 *
 * <p>OWASP #4(민감 데이터를 응답 바디·URL·로그에 노출하지 않는다)에 따라
 * 스택트레이스·SQL·내부 클래스명은 절대 담지 않는다.
 *
 * @param code    명세의 에러 코드 (예: AUTH_REQUIRED)
 * @param message 사용자에게 보여줄 안전한 메시지
 * @param fields  필드 단위 검증 실패 목록 (없으면 null)
 * @param at      발생 시각 (UTC)
 */
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldViolation> fields,
        Instant at
) {

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.name(), errorCode.getMessage(), null, Instant.now());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<FieldViolation> fields) {
        return new ApiErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                fields == null || fields.isEmpty() ? null : fields,
                Instant.now()
        );
    }

    /**
     * 어떤 필드가 왜 거부됐는지. 값 자체는 담지 않는다 —
     * 비밀번호·토큰이 입력이었던 경우 그대로 되돌려주는 사고를 막는다.
     */
    public record FieldViolation(String field, String reason) {
    }
}
