package com.autoinstagram.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청.
 *
 * <p>SKL-INPUT-VALIDATION 규칙 1·2(강제는 서버 측에서, 화이트리스트 우선):
 * username 은 "허용할 문자"를 정규식으로 명시하고 나머지는 거부한다.
 * 위험 문자만 골라 막는 블랙리스트는 새 우회 패턴을 막지 못한다.
 *
 * @param username 로그인 아이디 — 영문·숫자·. _ - 만 허용
 * @param password 비밀번호 — 문자 종류는 제한하지 않는다(사용자가 긴 암호구를 쓸 수 있어야 한다)
 */
public record LoginRequest(

        @NotBlank(message = "아이디를 입력해 주세요")
        @Size(min = 3, max = 100, message = "아이디는 3~100자여야 합니다")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다")
        String username,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Size(min = 8, max = 200, message = "비밀번호는 8~200자여야 합니다")
        String password
) {

    /**
     * 비밀번호를 절대 포함하지 않는다.
     * (record 의 기본 toString 은 모든 필드를 출력하므로 반드시 덮어써야 한다 — OWASP #4)
     */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "', password=***}";
    }
}
