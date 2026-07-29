package com.autoinstagram.backend.config;

import com.autoinstagram.backend.auth.jwt.JwtTokenProvider;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 감사 컬럼 자동 채움 설정.
 *
 * <p>skills/db/soft-delete-soft-delete-audit.md 규칙 2 는 '언제'뿐 아니라 '누가'도 요구한다
 * (created_by / updated_by / deleted_by). 여기서 현재 로그인 사용자를 공급한다.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** 인증된 요청이 아닌 경로(시드·스케줄러 등)에서 쓰는 작성자 이름. */
    static final String SYSTEM_ACTOR = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM_ACTOR);
            }
            if (authentication.getPrincipal() instanceof JwtTokenProvider.AuthenticatedUser user) {
                return Optional.of(user.username());
            }
            // 예상치 못한 주체 타입이어도 감사 컬럼이 비지 않도록 기본값을 준다
            return Optional.of(SYSTEM_ACTOR);
        };
    }
}
