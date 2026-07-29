package com.autoinstagram.backend.common.error;

/**
 * 명세된 에러 코드로 응답하기 위한 애플리케이션 예외.
 *
 * <p>SKL-ERROR-HANDLING-RESILIENCE 규칙 6(메시지는 청중별로)에 따라 두 개의 메시지를 분리해서 갖는다:
 * <ul>
 *   <li>{@code errorCode.getMessage()} — 사용자에게 내보내는 안전한 메시지 (응답 바디)</li>
 *   <li>{@link #getLogDetail()} — 로그에만 남기는 상세 컨텍스트 (응답에 절대 포함하지 않음)</li>
 * </ul>
 * 상세 컨텍스트를 만들 때 토큰·비밀번호가 섞이지 않도록
 * {@link com.autoinstagram.backend.common.util.TokenMasker} 를 반드시 거친다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String logDetail;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ApiException(ErrorCode errorCode, String logDetail) {
        this(errorCode, logDetail, null);
    }

    public ApiException(ErrorCode errorCode, String logDetail, Throwable cause) {
        // 예외 메시지 자체는 로그용 상세를 쓴다. 응답 바디는 errorCode 의 메시지만 사용한다.
        super(logDetail != null ? logDetail : errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.logDetail = logDetail;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getLogDetail() {
        return logDetail;
    }
}
