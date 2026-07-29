package com.autoinstagram.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

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
// 예약 발행 실행기(PublishWorker)가 동작하려면 스케줄링이 켜져 있어야 한다.
// 이게 없으면 scheduledAt 을 저장만 하고 아무도 실행하지 않아 예약 발행이 죽은 기능이 된다.
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
