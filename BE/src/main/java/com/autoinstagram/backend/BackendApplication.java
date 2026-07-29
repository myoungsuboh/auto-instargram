package com.autoinstagram.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Instagram Automation Backend (3_architecture.md SVC-02).
 *
 * <p>바운디드 컨텍스트 (2_ddd.md):
 * <ul>
 *   <li>{@code security} — CTX-02 인스타그램 보안 관리 (SecurityCredential)</li>
 *   <li>{@code post} — CTX-01 인스타그램 게시 관리 (QueueItem, HistoryRecord)</li>
 *   <li>{@code auth} — 대시보드 로그인 (명세 외 추가, 사용자 확정 결정)</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
