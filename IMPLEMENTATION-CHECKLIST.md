# ✅ IMPLEMENTATION-CHECKLIST — auto-instargram

> 이 목록은 설계 그래프에서 **기계적으로 생성**되었습니다 — 명세 요약 과정의 누락이 없습니다.
> 
> **사용법 (AI 에이전트 필독):** 구현이 끝났다고 판단되면 이 파일의 각 항목을 실제 코드와
> 대조해 `- [x]` 로 바꾸고, 항목 끝 `←구현위치:` 뒤에 **실제 파일 경로**를 적으세요.
> 경로를 적은 파일은 실제로 **열어서 확인**하세요 — 열 수 없는 경로는 거짓 체크입니다.
> 경로를 적을 수 없는 항목은 **미구현**입니다 — 구현한 뒤 다시 대조하세요.
> **모든 항목(26개)이 [x] 가 될 때까지 이 루프를 반복**한 뒤에만 완료를 보고하세요.

## APIs (5)
- [ ] `POST /api/v1/queues` — 예약 큐 등록 및 관리 API [→ Instagram Automation Backend]  ←구현위치: 
- [ ] `GET /api/v1/queues` — 예약 큐 목록 조회 API [→ Instagram Automation Backend]  ←구현위치: 
- [ ] `GET /api/v1/history` — 게시 이력 조회 API [→ Instagram Automation Backend]  ←구현위치: 
- [ ] `POST /api/v1/reels/upload` — 릴스 업로드 파이프라인 실행 API [→ Instagram Automation Backend]  ←구현위치: 
- [ ] `POST /api/v1/tokens/refresh` — 토큰 교환 및 갱신 API [→ Instagram Automation Backend]  ←구현위치: 

## Entities (3)
- [ ] Entity `HistoryRecord` (속성 4개)  ←구현위치: 
- [ ] Entity `QueueItem` (속성 4개)  ←구현위치: 
- [ ] Entity `SecurityCredential` (속성 3개)  ←구현위치: 

## Policies (비즈니스 규칙) (4)
- [ ] Policy `POL-01` — 모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함  ←구현위치: 
- [ ] Policy `POL-02` — history.json 쓰기 작업은 원자적(Atomic) 방식을 보장하여 동시성 충돌이나 파일 손상을 방지해야 함  ←구현위치: 
- [ ] Policy `POL-03` — 목록 조회는 결과 0건일 때 빈 배열을 200 으로 정상 반환한다  ←구현위치: 
- [ ] Policy `POL-05` — 모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함  ←구현위치: 

## Policies — NFR (성능·비기능 규칙) (1)
> 아래 NFR 항목은 단일 구현 파일이 아니라 **검증 방법/증거**(부하테스트 명령·모니터링 설정·측정 문서 경로)를 마커 뒤에 적으세요 — 파일 경로 강요는 거짓 체크를 유도합니다.
- [ ] Policy `POL-04` — API 응답 시간은 3초 이내로 유지되어야 함  ←구현위치: 

## Screens (화면) (4)
- [ ] Screen `자동 업로드 대시보드` (`/dashboard/upload`) (→ API: API-02, API-01)  ←구현위치: 
- [ ] Screen `자동 게시 관리 대시보드` (`/dashboard/posts`) (→ API: API-02)  ←구현위치: 
- [ ] Screen `CLI 인터페이스 및 로그 대시보드` (`/dashboard/history`) (→ API: API-03)  ←구현위치: 
- [ ] Screen `릴스 업로드 제어 화면` (`/dashboard/reels`) (→ API: API-05)  ←구현위치: 

## Aggregates (정합성 경계) (3)
- [ ] Aggregate `HistoryRecord` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: 
- [ ] Aggregate `QueueItem` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: 
- [ ] Aggregate `SecurityCredential` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: 

## Domain Events (3)
- [ ] Domain Event `QueueItemFailed`  ←구현위치: 
- [ ] Domain Event `HistoryRecordCreated`  ←구현위치: 
- [ ] Domain Event `TokenRefreshed`  ←구현위치: 

## Services / Databases (3)
- [ ] Service `Mobile Web Frontend` (Vue.js)  ←구현위치: 
- [ ] Service `Instagram Automation Backend` (Spring Boot)  ←구현위치: 
- [ ] Database `Primary RDBMS` (PostgreSQL)  ←구현위치: 
