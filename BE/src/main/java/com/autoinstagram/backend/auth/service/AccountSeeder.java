package com.autoinstagram.backend.auth.service;

import com.autoinstagram.backend.auth.domain.AccountRole;
import com.autoinstagram.backend.auth.domain.AppAccount;
import com.autoinstagram.backend.auth.domain.AppAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 운영 계정 시드.
 *
 * <p>00-ORCHESTRATOR Verify 2 요구사항: "seed initial admin credentials <b>idempotently</b>".
 *
 * <p><b>멱등성 처리 방식</b> — 계정별로 존재 여부를 확인해 없는 것만 만든다.
 * "계정이 하나라도 있으면 전체를 건너뛰기" 로 구현하지 않은 이유:
 * 그러면 나중에 운영자 계정을 추가해도 영원히 생성되지 않는다
 * (ORCHESTRATOR 의 "skip a table that already has rows — NOT the whole database" 와 같은 취지).
 *
 * <p>비밀번호는 코드에 없고 환경변수에서 주입된다 (SKL-SECRETS-MANAGEMENT 규칙 1).
 */
@Component
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    /** .env.example 에 들어 있는 예시 비밀번호. 이 값이 그대로 쓰이면 경고한다. */
    private static final String EXAMPLE_PASSWORD_MARKER = "Local";

    private final AppAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean seedEnabled;
    private final String adminUsername;
    private final String adminPassword;
    private final String operatorUsername;
    private final String operatorPassword;

    public AccountSeeder(AppAccountRepository accountRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.seed.enabled:true}") boolean seedEnabled,
                         @Value("${app.seed.admin-username:}") String adminUsername,
                         @Value("${app.seed.admin-password:}") String adminPassword,
                         @Value("${app.seed.operator-username:}") String operatorUsername,
                         @Value("${app.seed.operator-password:}") String operatorPassword) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.operatorUsername = operatorUsername;
        this.operatorPassword = operatorPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("초기 계정 시드가 비활성화되어 있습니다 (app.seed.enabled=false)");
            return;
        }
        seedIfAbsent(adminUsername, adminPassword, AccountRole.SYSTEM_ADMIN);
        seedIfAbsent(operatorUsername, operatorPassword, AccountRole.SYSTEM_OPERATOR);
    }

    private void seedIfAbsent(String username, String rawPassword, AccountRole role) {
        if (username == null || username.isBlank()) {
            log.debug("{} 계정 시드를 건너뜁니다 — 아이디가 설정되지 않음", role);
            return;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            // 아이디는 있는데 비밀번호가 없으면 설정 실수다. 조용히 넘기지 않는다.
            log.error("{} 계정 '{}' 의 비밀번호가 설정되지 않아 시드하지 못했습니다. "
                    + ".env 의 app.seed.*-password 값을 확인하세요.", role, username);
            return;
        }

        // ── 멱등성: 이미 있으면 아무것도 하지 않는다 (비밀번호도 덮어쓰지 않는다) ──
        if (accountRepository.existsByUsernameAndDeletedAtIsNull(username)) {
            log.info("{} 계정 '{}' 이 이미 있어 시드를 건너뜁니다", role, username);
            return;
        }

        AppAccount account = AppAccount.create(
                username,
                passwordEncoder.encode(rawPassword),
                role);
        accountRepository.save(account);

        // 비밀번호는 절대 로그에 남기지 않는다 (POL-05 / OWASP #4)
        log.info("{} 계정 '{}' 을 생성했습니다", role, username);

        if (rawPassword.contains(EXAMPLE_PASSWORD_MARKER)) {
            log.warn("""
                    ⚠️  계정 '{}' 이 .env.example 의 예시 비밀번호로 생성되었습니다.
                        로컬 개발 외의 환경에서는 반드시 .env 의 비밀번호를 변경하고
                        해당 계정을 다시 만드세요.""", username);
        }
    }
}
