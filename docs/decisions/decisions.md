---
description: 의사결정(ADR) 색인 — 왜 그렇게 했는지. AI 빠른 인덱싱용.
---

# Decisions Index

| ADR ID | Decision | 단계(Origin) | Created At | Status | File |
|--------|----------|--------------|------------|--------|------|
| ADR-0001 | PostgreSQL 기동 수단으로 Docker Desktop 채택 | dev (execute-dev) | 2026-07-29 11:31 | 승인됨 | [2026-07-29-postgresql-via-docker-desktop.md](2026-07-29-postgresql-via-docker-desktop.md) |
| ADR-0002 | Spring Boot 버전을 4.0.7 로 핀 | dev (execute-dev) | 2026-07-29 11:31 | 승인됨 | [2026-07-29-spring-boot-4-0-7-pin.md](2026-07-29-spring-boot-4-0-7-pin.md) |
| ADR-0003 | status enum 충돌 — 내부 상태와 API 응답을 분리 | dev (execute-dev) | 2026-07-29 11:31 | 승인됨 | [2026-07-29-status-enum-internal-vs-api.md](2026-07-29-status-enum-internal-vs-api.md) |
| ADR-0004 | DB PK 컬럼명은 `id`, 명세의 식별자 이름은 API 응답에서 매핑 | dev (execute-dev) | 2026-07-29 11:31 | 승인됨 | [2026-07-29-db-primary-key-named-id.md](2026-07-29-db-primary-key-named-id.md) |
| ADR-0005 | 대시보드 로그인 기능 추가 (명세 외 API 4개) | dev (execute-dev) | 2026-07-29 12:30 | 승인됨 | [2026-07-29-dashboard-login-added.md](2026-07-29-dashboard-login-added.md) |
| ADR-0006 | 인증 토큰 전달 — 쿠키 우선 + Bearer 병행, CSRF 는 SameSite 로 | dev (execute-dev) | 2026-07-29 12:30 | 승인됨 | [2026-07-29-auth-token-transport.md](2026-07-29-auth-token-transport.md) |
| ADR-0007 | 갱신 토큰은 JWT 가 아닌 불투명 난수 + DB 해시 | dev (execute-dev) | 2026-07-29 12:32 | 승인됨 | [2026-07-29-opaque-refresh-token.md](2026-07-29-opaque-refresh-token.md) |
| ADR-0008 | 토큰 저장 암호화는 AES-256-GCM + 전용 키 환경변수 | dev (execute-dev) | 2026-07-29 12:32 | 승인됨 | [2026-07-29-token-at-rest-encryption.md](2026-07-29-token-at-rest-encryption.md) |
| ADR-0009 | 인스타그램 토큰 교환은 재시도하지 않는다 (비멱등) | dev (execute-dev) | 2026-07-29 12:32 | 승인됨 | [2026-07-29-no-retry-on-token-exchange.md](2026-07-29-no-retry-on-token-exchange.md) |
| ADR-0010 | 통합 테스트는 실제 PostgreSQL 에 대해 실행한다 | dev (execute-dev) | 2026-07-29 12:32 | 승인됨 | [2026-07-29-integration-tests-use-real-postgres.md](2026-07-29-integration-tests-use-real-postgres.md) |
| ADR-0011 | 오브젝트 스토리지를 추가하지 않는다 (로컬 파일 경로 유지) | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-no-object-storage.md](2026-07-29-no-object-storage.md) |
| ADR-0012 | 릴스 업로드는 QueueItem 애그리거트를 재사용한다 | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-reels-reuse-queue-aggregate.md](2026-07-29-reels-reuse-queue-aggregate.md) |
| ADR-0013 | 실제 게시는 백그라운드 워커가 수행한다 | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-background-publish-worker.md](2026-07-29-background-publish-worker.md) |
| ADR-0014 | POL-01 감사 추적을 별도 append-only 테이블로 분리 | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-publish-attempts-audit-table.md](2026-07-29-publish-attempts-audit-table.md) |
| ADR-0015 | 예약 선점은 조건부 UPDATE 로 원자화한다 | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-atomic-queue-claim.md](2026-07-29-atomic-queue-claim.md) |
| ADR-0016 | 멱등성은 전용 테이블로 구현한다 | dev (execute-dev) | 2026-07-29 14:31 | 승인됨 | [2026-07-29-idempotency-separate-table.md](2026-07-29-idempotency-separate-table.md) |
| ADR-0017 | 바이너리 사전 검증의 범위를 한정한다 | dev (execute-dev) | 2026-07-29 14:34 | 승인됨 | [2026-07-29-binary-validation-scope.md](2026-07-29-binary-validation-scope.md) |
| ADR-0018 | 미디어 해시는 파일을 읽을 수 없으면 경로 기준으로 대체한다 | dev (execute-dev) | 2026-07-29 14:34 | 승인됨 | [2026-07-29-media-hash-fallback.md](2026-07-29-media-hash-fallback.md) |
