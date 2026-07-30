package com.autoinstagram.backend.security.api;

import com.autoinstagram.backend.security.api.dto.TokenRefreshRequest;
import com.autoinstagram.backend.security.api.dto.TokenRefreshResponse;
import com.autoinstagram.backend.security.service.SecurityCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-05 {@code POST /api/v1/tokens/refresh} — 토큰 교환 및 갱신 API.
 *
 * <p>1_spack.md:
 * <ul>
 *   <li>구현 Story: Story-06.2 "토큰 자동 갱신 및 안전 저장"</li>
 *   <li>인증: {@code required=true}, {@code required_roles=[system_admin]}
 *       → 관리자만 호출 가능. 강제는 {@link com.autoinstagram.backend.config.SecurityConfig} 에서
 *       경로 단위로 한다 (SKL-AUTHN-AUTHZ 규칙 3: 서버 측 검증)</li>
 *   <li>에러: 401 AUTH_REQUIRED / 422 INVALID_TOKEN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController {

    private final SecurityCredentialService credentialService;

    public TokenController(SecurityCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 단기 토큰을 장기 토큰으로 교환하고 암호화 저장한다.
     *
     * <p>명세가 200 OK 를 규정하므로 201 이 아니라 200 을 반환한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        var result = credentialService.refreshAccessToken(request.shortLivedToken());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new TokenRefreshResponse(result.accessToken(), result.expiresInSeconds(),
                        result.igUsername()));
    }
}
