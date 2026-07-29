# auto-instargram

인스타그램 릴스를 **예약해 두면 자동으로 게시**해 주는 관리 도구입니다.
영상을 미리 등록해 두고 발행 시각을 정하면, 서버가 그 시각에 인스타그램으로 올립니다.
올린 결과와 실패 이력도 화면에서 확인할 수 있습니다.

---

## 🚀 실행하는 방법 (비개발자용)

### 준비물 — 딱 3가지

| 필요한 것 | 확인 방법 | 없으면 |
|---|---|---|
| **Docker Desktop** | 시작 메뉴에 "Docker Desktop" 이 있는지 | [여기서 설치](https://www.docker.com/products/docker-desktop/) |
| **Java 17 이상** | 명령창에 `java -version` | [여기서 설치](https://adoptium.net/) |
| **Node.js 20 이상** | 명령창에 `node -v` | [여기서 설치](https://nodejs.org/) |

> Docker Desktop 은 **설치 후 실행까지** 해주세요. 상태바에 고래 아이콘이 보이면 준비된 것입니다.

### 실행

**Windows** — 파일 탐색기에서 `run.bat` 을 **두 번 클릭**하세요.

**macOS / Linux** — 터미널에서:

```bash
./run.sh
```

그러면 스크립트가 알아서 다 합니다:

1. Docker 가 켜져 있는지 확인
2. 설정 파일(`.env`)이 없으면 예시에서 만들어 줌
3. 데이터베이스 실행하고 준비될 때까지 기다림
4. 서버와 화면을 각각 새 창에서 실행
5. 준비되면 **브라우저를 자동으로 열어 줌**

처음 실행은 라이브러리를 받아오느라 **1~2분** 걸립니다. 두 번째부터는 훨씬 빠릅니다.

### 로그인

브라우저가 열리면 로그인 화면이 나옵니다.

| 구분 | 아이디 | 비밀번호 |
|---|---|---|
| 관리자 | `admin` | `Admin!2026Local` |
| 운영자 | `operator` | `Operator!2026Local` |

> 이 값은 `.env` 파일의 `SEED_ADMIN_PASSWORD` 등에서 바꿀 수 있습니다.
> **⚠️ 인터넷에 공개하려면 반드시 바꾸세요.** 예시용 비밀번호입니다.

### 끄기

**Windows** — `stop.bat` 두 번 클릭
**macOS / Linux** — `./stop.sh`

데이터(예약·이력)는 보존됩니다. 완전히 지우고 처음부터 시작하려면:

```bash
docker compose -f docker-compose.dev.yml down -v
```

---

## 📺 화면 안내

| 화면 | 주소 | 하는 일 |
|---|---|---|
| **예약 등록** | `/dashboard/upload` | 영상과 발행 시각을 정해 예약합니다 |
| **게시 관리** | `/dashboard/posts` | 진행 상태와 실패·재시도 현황을 봅니다 |
| **이력** | `/dashboard/history` | 지금까지의 게시 결과를 기간별로 봅니다 |
| **릴스** | `/dashboard/reels` | 지금 바로 올리기 + 인스타그램 토큰 갱신(관리자) |

### 영상 파일은 어디에 두나요?

**`BE/storage/media/` 폴더**에 영상 파일을 넣고, 화면에서는 **파일명만** 입력하세요.
(예: `reel-morning.mp4`)

이 폴더는 서버가 처음 실행될 때 자동으로 만들어집니다.
보안상 **이 폴더 밖의 파일은 지정할 수 없습니다** — 서버의 다른 파일을 읽어 가는 공격을 막기 위해서입니다.

> 웹에서 드래그해 업로드하는 기능은 없습니다. 설계 문서가 "로컬 파일 경로"를 받도록 규정했기 때문입니다
> ([근거](docs/decisions/2026-07-29-no-object-storage.md)).

### 지원 형식

`.mp4` · `.mov` · `.m4v` — 확장자만 바꾼 파일은 내용 검사에서 거부됩니다.

---

## ⚠️ 인스타그램 실제 게시에 대해

기본 설정에서는 **인스타그램에 실제로 올리지 않습니다.** 파일 검증·예약 등록·이력 기록까지는
모두 정상 동작하고, 마지막 "실제 게시" 단계만 "설정 없음" 오류로 멈춥니다.

실제로 게시하려면 인스타그램 **프로페셔널 계정**(비즈니스 또는 크리에이터, **공개** 상태)과
액세스 토큰이 필요합니다. `.env` 에서:

```
INSTAGRAM_PUBLISH_ENABLED=true
INSTAGRAM_USER_ID=<인스타그램 계정 번호 — 페이스북 페이지 ID 가 아닙니다>
INSTAGRAM_CLIENT_SECRET=<Instagram 앱 시크릿 — Facebook 앱 시크릿이 아닙니다>
```

그리고 화면의 **릴스 → 토큰 갱신**에서 단기 토큰을 넣어 장기 토큰으로 교환하세요.

### 토큰은 어떻게 받나요?

**화면에 안내가 들어 있습니다.** `릴스` 화면의 **"어떻게 받나요?"** 버튼을 누르면
Meta 공식 문서 기준의 발급 절차가 단계별로 나옵니다 (15~20분 소요).

특히 **처음 앱을 만들 때 잘못 고르면 아예 진행할 수 없는** 지점이 있어서, 미리 적어 둡니다:

| Meta 가 물어보는 것 | 골라야 하는 값 |
|---|---|
| 이용 사례 | **기타 (Other)** |
| 앱 유형 | **비즈니스** |

이용 사례에서 "비즈니스용 Facebook"·마케팅 API 같은 다른 항목을 고르면 **Instagram 제품이
목록에 나타나지 않습니다.** 이미 만든 앱에 Instagram 이 있는지는 Meta 앱 대시보드의
**게시** 메뉴 → "이 앱의 이용 사례" 목록에서 확인할 수 있습니다.

> **이미 다른 용도(광고 등)로 쓰던 앱에도 추가할 수 있습니다.** 그 앱의 대시보드에는
> 제품 카드가 없고 "앱 맞춤 설정 및 요건" 목록만 보이는데, 오른쪽 위 **이용 사례 추가**
> 를 누르고 목록에서 **"Instagram에서 메시지 및 콘텐츠 관리"** 를 체크해 저장하면 됩니다.
>
> 추가한 뒤에는 반드시 **"Instagram 로그인이 포함된 API 설정"** 을 쓰세요 —
> "Facebook 로그인이 포함된 API 설정" 은 이 프로그램이 쓰지 않는 방식입니다.

그 화면에서 특히 틀리기 쉬운 두 가지:

| 항목 | 주의 |
|---|---|
| `INSTAGRAM_CLIENT_SECRET` | 화면 맨 위의 **"Instagram 앱 시크릿 코드"** 입니다. `앱 설정 → 기본 설정` 의 앱 시크릿(Facebook 앱 쪽)을 넣으면 토큰 교환이 실패합니다 |
| 게시 권한 | 1번 항목의 `Add all required permissions` 는 **메시지·댓글 권한만** 넣어 줍니다. 릴스 게시에 필요한 `instagram_business_content_publish` 는 **권한 및 기능** 페이지에서 직접 추가하세요 |

Webhooks 구성 · Instagram 비즈니스 로그인 설정 · 앱 검수는 이 프로그램에 필요 없어 건너뛰어도 됩니다.

**페이스북 페이지는 만들지 않아도 됩니다.** 인터넷 자료 다수가 페이지를 요구하는데, 그건
다른 연동 방식(Facebook 로그인) 설명입니다 —
[근거와 상세](docs/decisions/2026-07-29-instagram-login-api-path.md).

> 자격 증명이 없을 때 성공을 흉내내지 않는 것은 의도된 설계입니다 —
> 이력에 "게시됨"이라고 거짓이 남는 것이 실패보다 나쁩니다.

---

## 🔒 인터넷에 공개하기 전에 (운영 체크리스트)

이 프로젝트는 **로컬 실행에 맞춰 설정**되어 있습니다. 공개 서버에 올릴 때는 `.env` 에서 아래를 반드시 바꾸세요.

| 항목 | 로컬 기본값 | 운영에서 | 안 바꾸면 |
|---|---|---|---|
| `HTTPS_ENFORCED` | `false` | **`true`** | 로그인 정보가 **평문**으로 전송됩니다 |
| `JWT_COOKIE_SECURE` | `false` | **`true`** | 인증 쿠키가 암호화되지 않은 연결로 오갑니다 |
| `POSTGRES_PASSWORD` | 예시값 | **새 값** | 누구나 데이터베이스에 접속할 수 있습니다 |
| `JWT_SECRET` | 무작위 생성 | **새 값** | 로그인 토큰을 위조할 수 있습니다 |
| `CREDENTIAL_ENCRYPTION_KEY` | 무작위 생성 | **새 값** | 저장된 인스타그램 토큰이 복호화될 수 있습니다 |
| `SEED_ADMIN_PASSWORD` | 예시값 | **새 값** | 누구나 관리자로 로그인할 수 있습니다 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | **실제 화면 주소** | 화면이 서버와 통신하지 못합니다 |

`HTTPS_ENFORCED` 와 `JWT_COOKIE_SECURE` 는 **항상 함께** 켜야 합니다
([근거](docs/decisions/2026-07-29-https-enforcement-defaults-off.md)).

> `HTTPS_ENFORCED=true` 는 **로컬에서 켜지 마세요.** 브라우저가 이후 접속을 전부 https 로 강제해
> 로컬 개발이 불가능해지고, 서버 설정을 되돌려도 복구되지 않습니다.

---

## 🧱 무엇으로 만들었나

| 구성 | 기술 | 위치 |
|---|---|---|
| 화면 | Vue 3.5 + Vite 8 | `FE/` |
| 서버 | Spring Boot 4.0.7 + Java 17 | `BE/` |
| 데이터베이스 | PostgreSQL 17 | `docker-compose.dev.yml` |

설계 문서는 저장소 루트에 함께 있습니다: `1_spack.md`(API·데이터) · `2_ddd.md`(도메인) ·
`3_architecture.md`(구성) · `IMPLEMENTATION-CHECKLIST.md`(구현 대조표).

**왜 그렇게 만들었는지**는 `docs/decisions/` 에 21건의 기록으로 남겨 두었습니다
([목록](docs/decisions/decisions.md)) — 설계 문서끼리 어긋난 지점, 스스로 판단해 정한 것,
되돌리기 어려운 선택의 이유가 들어 있습니다.

---

## 🛠 개발자용

```bash
# 데이터베이스만 띄우기
docker compose -f docker-compose.dev.yml up -d

# 백엔드 (테스트는 PostgreSQL 이 떠 있어야 합니다)
cd BE && ./gradlew test          # 테스트 145건
cd BE && ./gradlew bootRun       # http://localhost:8080

# 프론트엔드
cd FE && npm install
cd FE && npm run dev             # http://localhost:5173
cd FE && npm run lint            # ESLint (경고 0 기준)
cd FE && npm run build
```

### 확인용 엔드포인트

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

### 알아두면 좋은 것

- **테스트는 실제 PostgreSQL 을 씁니다.** 인메모리 DB 로 바꾸면 마이그레이션의 CHECK 제약과
  partial unique index 가 검증되지 않습니다 ([근거](docs/decisions/2026-07-29-integration-tests-use-real-postgres.md)).
- **Gradle 로 띄운 백엔드는 창을 닫아도 자식 JVM 이 남습니다.** 포트 8080 이 계속 잡혀 있으면
  `stop.bat` / `stop.sh` 를 쓰세요.
- 예약은 백그라운드 작업이 10초마다 확인해 실행합니다. `PUBLISH_WORKER_ENABLED=false` 로 끌 수 있습니다.
