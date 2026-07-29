package com.autoinstagram.backend.auth.api.dto;

import com.autoinstagram.backend.auth.domain.AccountRole;

/**
 * 로그인·세션 조회 응답.
 *
 * <p><b>토큰이 응답 바디에 없는 것이 의도된 설계다.</b>
 * SKL-AUTHN-AUTHZ 규칙 1 에 따라 액세스·갱신 토큰은 httpOnly 쿠키로만 전달되며,
 * 화면의 JavaScript 는 토큰을 읽을 수 없다(XSS 로 탈취 불가).
 * OWASP #4(민감 데이터를 응답 바디에 노출하지 않는다)도 같은 요구다.
 *
 * @param username  로그인한 아이디
 * @param role      1_spack.md 표기의 권한 이름 (system_admin / system_operator)
 * @param expiresIn 액세스 토큰 남은 수명(초). 화면이 이 값으로 자동 갱신 시점을 잡는다
 */
public record SessionResponse(
        String username,
        String role,
        long expiresIn
) {

    public static SessionResponse of(String username, AccountRole role, long expiresInSeconds) {
        // 화면·문서와 표기를 맞추기 위해 명세의 소문자 표기를 내보낸다 (AccountRole.getSpecName)
        return new SessionResponse(username, role.getSpecName(), expiresInSeconds);
    }
}
