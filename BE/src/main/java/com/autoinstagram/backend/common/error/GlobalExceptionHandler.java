package com.autoinstagram.backend.common.error;

import com.autoinstagram.backend.common.util.TokenMasker;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 예외를 1_spack.md 의 에러 표 형식으로 변환하는 단일 지점.
 *
 * <p>적용 규칙:
 * <ul>
 *   <li>SKL-ERROR-HANDLING-RESILIENCE 규칙 1 — 에러를 삼키지 않는다. 모든 분기에서 로그를 남긴다.</li>
 *   <li>SKL-ERROR-HANDLING-RESILIENCE 규칙 6 — 사용자에겐 안전한 메시지, 로그엔 상세 컨텍스트.</li>
 *   <li>SKL-OWASP-TOP10 규칙 4 — 민감 데이터를 응답·로그에 노출하지 않는다.
 *       로그로 나가는 모든 자유 형식 문자열은 {@link TokenMasker#scrub(String)} 을 거친다.</li>
 *   <li>POL-01 — 실패 경로를 누락 없이 기록한다.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 명세된 에러 코드로 의도적으로 던진 예외. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        String detail = TokenMasker.scrub(ex.getLogDetail());

        // 4xx 는 호출자 잘못이므로 warn, 5xx 는 우리 쪽 문제이므로 error + 스택트레이스
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {} — {}", code.name(), code.getStatus().value(), detail, ex);
        } else {
            log.warn("[{}] {} — {}", code.name(), code.getStatus().value(), detail);
        }
        return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code));
    }

    /**
     * {@code @Valid} 로 걸러진 요청 본문 검증 실패.
     * 1_spack.md API-01 은 "미디어 경로 누락 또는 잘못된 시간 형식" 을 422 VALIDATION_ERROR 로 규정한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                // 거부 이유만 담는다 — 입력값 자체는 담지 않는다(비밀번호·토큰 반향 방지)
                .map(fe -> new ApiErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("[VALIDATION_ERROR] 422 — 필드 검증 실패 {}", fields);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, fields));
    }

    /** {@code @Validated} 파라미터(쿼리스트링 등) 검증 실패. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorResponse.FieldViolation> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldViolation(
                        v.getPropertyPath() == null ? "request" : v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();
        log.warn("[VALIDATION_ERROR] 422 — 파라미터 검증 실패 {}", fields);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, fields));
    }

    /**
     * JSON 자체가 깨졌거나 타입이 안 맞는 경우 → 400 (1_spack.md "에러 응답 가이드": 요청 본문 검증 실패).
     * 파싱 실패 메시지에는 원본 본문 일부가 담길 수 있어 반드시 scrub 한다.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception ex) {
        log.warn("[BAD_REQUEST] 400 — {}", TokenMasker.scrub(ex.getMessage()));
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.BAD_REQUEST));
    }

    /**
     * 마지막 안전망. 여기까지 온 예외는 예상하지 못한 것이므로 스택트레이스를 남긴다(규칙 1: 삼키지 않는다).
     * 응답에는 내부 정보를 일절 담지 않는다(OWASP #4).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("[INTERNAL_ERROR] 500 — 처리되지 않은 예외: {}",
                TokenMasker.scrub(ex.getMessage()), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
