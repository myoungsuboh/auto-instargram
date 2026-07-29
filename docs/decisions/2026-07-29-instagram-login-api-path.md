---
description: Meta 의 두 인스타그램 API 경로 중 "Instagram Login" 을 쓴다 — 두 경로는 권한·토큰·엔드포인트가 달라 섞으면 동작하지 않는다
tags: [decision]
---

# ADR-0022: 인스타그램 연동은 "Instagram Login" 경로를 쓴다

- 기록일: 2026-07-29 16:27
- 갱신: 2026-07-29 — 실제 발급을 시도하다 막혀 공식 문서를 재확인했다.
  결정 자체는 그대로이고, 빠져 있던 **후속 제약 3건**(앱 생성 시 이용 사례·앱 유형,
  페이스북 페이지 불필요, 권한 수동 추가 불가)과 출처 2건을 추가했다.
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — API-04 / API-05](../../1_spack.md), [3_architecture.md — SVC-02 외부 의존성](../../3_architecture.md)
- 선행 결정: [ADR-0009](2026-07-29-no-retry-on-token-exchange.md)
- 구현: [security/service/InstagramGraphClient.java](../../BE/src/main/java/com/autoinstagram/backend/security/service/InstagramGraphClient.java) · [post/service/InstagramReelsPublisher.java](../../BE/src/main/java/com/autoinstagram/backend/post/service/InstagramReelsPublisher.java) · [FE/src/components/TokenGuideModal.vue](../../FE/src/components/TokenGuideModal.vue)

## 맥락 (Context)

Meta 공식 문서를 확인한 결과(2026-07), 인스타그램에 API 로 게시하는 방법이
**서로 호환되지 않는 두 경로**로 나뉘어 있다:

| | Instagram API **with Instagram Login** | Instagram API **with Facebook Login** |
|---|---|---|
| 호스트 | `graph.instagram.com` | `graph.facebook.com` |
| 권한(scope) | `instagram_business_basic`, `instagram_business_content_publish` | `instagram_basic`, `instagram_content_publish`, `pages_read_engagement` (+경우에 따라 `ads_management`, `ads_read`) |
| 토큰 종류 | Instagram User access token | **Facebook Page** access token |
| 장기 토큰 교환 | `grant_type=ig_exchange_token` | `grant_type=fb_exchange_token` |
| 계정 ID 확인 | `GET /me?fields=user_id,username` | `GET /me/accounts` → `GET /{page-id}?fields=instagram_business_account` |
| 토큰 발급 (간편) | 앱 대시보드 → Instagram → **API setup with Instagram business login** → **Generate token** | Graph API Explorer 로그인 |

Phase 2 구현 시 `graph.instagram.com` + `ig_exchange_token` 을 선택했는데,
**두 경로가 존재하며 섞일 수 없다는 사실을 명시적으로 기록하지 않았다.**
화면에 발급 안내 모달을 만들면서 이 차이가 드러났다.

## 결정 (Decision)

**Instagram Login 경로만 쓴다.** 코드·안내 문서·설정 이름을 모두 이 경로에 맞춘다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| Instagram Login 경로 | 사용자가 앱 대시보드에서 **버튼 한 번(Generate token)** 으로 토큰을 받을 수 있다 — OAuth 리다이렉트 구현이 필요 없다. 페이스북 페이지 토큰·페이지 ID 조회 단계가 빠져 비개발자 안내가 짧아진다. 이미 구현된 코드와 일치 | 페이스북 페이지 관련 기능(페이지 인사이트 등)으로 확장할 수 없다 | **채택** |
| Facebook Login 경로 | 페이스북 페이지 기능까지 함께 다룰 수 있다 | 토큰 발급에 단계가 더 많다(Graph API Explorer → `/me/accounts` → 페이지 ID → IG 계정 ID). 이미 구현한 코드를 전면 수정해야 한다. 비개발자 안내가 훨씬 길어진다 | 기각 |
| 둘 다 지원 | 사용자가 편한 쪽을 고를 수 있다 | 권한·토큰·엔드포인트가 전부 달라 사실상 두 개의 연동을 만드는 일이다. 명세에 그런 요구가 없다 | 기각 |

## 근거 (Rationale)

이 프로젝트의 목표에 Instagram Login 경로가 더 맞다:
`00-ORCHESTRATOR` Principle 10 이 "비개발자가 실행할 수 있어야 한다"를 요구하고,
Instagram Login 은 **앱 대시보드에서 Generate token 버튼 하나**로 토큰이 나온다 —
OAuth 리다이렉트 구현도, 페이지 ID 를 거치는 2단계 조회도 필요 없다.

Facebook Login 경로의 이점(페이지 인사이트 등)은 명세가 요구하지 않는다.
1_spack.md 의 API-04·API-05 는 릴스 게시와 토큰 교환만 다룬다.

## 영향 (Consequences)

- 긍정: 사용자 안내가 7단계로 끝난다(모달로 제공). 코드와 문서가 같은 경로를 가리켜 어긋나지 않는다.
  `.env` 항목 이름(`INSTAGRAM_USER_ID`, `INSTAGRAM_CLIENT_SECRET`)도 이 경로에 대응한다.
- 트레이드오프/비용: 페이스북 페이지 단위 기능으로 확장하려면 연동을 새로 만들어야 한다.
- 후속 제약 (중요):
  - **두 경로의 설정을 섞지 말 것.** `instagram_basic`·`pages_read_engagement` 같은
    Facebook Login 쪽 권한을 넣거나, `graph.facebook.com` 호스트를 섞으면 조용히 실패한다.
    인터넷의 인스타그램 API 자료 다수가 Facebook Login 경로를 설명하므로 특히 혼동하기 쉽다.
  - `INSTAGRAM_USER_ID` 는 **페이스북 페이지 ID 가 아니라** `GET https://graph.instagram.com/v25.0/me?fields=user_id,username`
    가 돌려주는 값이다.
  - **Meta 앱을 만들 때 이용 사례로 `기타(Other)`, 앱 유형으로 `비즈니스`를 골라야 한다.**
    다른 이용 사례(예: "비즈니스용 Facebook", 마케팅 API 광고)로 만든 앱에는
    Instagram 제품이 목록에 나타나지 않고, 왼쪽 메뉴에 Instagram 이 생기지 않는다.
    공식 문서 3단계: "Instagram 제품에 액세스할 수 있는 앱을 만들려면 Other 이용 사례를 선택합니다",
    4단계: "Instagram 제품을 추가하려면 앱이 비즈니스 유형 앱이어야 합니다".
    Instagram 제품은 **왼쪽 메뉴가 아니라 대시보드 화면 본문을 아래로 스크롤**해
    제품 카드의 "설정"을 눌러 추가한다. 기존 앱에 추가할 때도 이 단계부터 하면 된다.
    자기 앱에 Instagram 이 들어 있는지는 **게시(go_live) 페이지의 "이 앱의 이용 사례"** 목록으로 확인한다.
  - **페이스북 페이지는 필요 없다.** 문서 원문: "이 API 설정은 Facebook 페이지를
    Instagram 프로페셔널 계정에 연결할 필요가 없습니다." 페이지 연결은 Facebook Login
    경로의 조건이다 — 위 "두 경로를 섞지 말 것"의 구체적인 사례다.
    연결할 인스타그램 계정은 **공개 상태**여야 한다.
  - 다른 이용 사례의 권한 목록에서 `instagram_*` 권한을 손으로 찾아 추가할 수 없다.
    Instagram 제품을 추가하면 필요·권장 권한이 자동으로 붙는다.
  - 게시 한도는 **24시간 이동 기준 100건**이다 (공식 문서 명시).
    구현 초기에 근거 없이 25 로 넣었던 것을 이 조사에서 바로잡았다.
  - `PublishingLimitGuard` 는 **우리 이력 기준의 사전 점검**이다. 권위 있는 잔여 한도는
    `GET /{ig-user-id}/content_publishing_limit` 이 알려준다 — 같은 계정을 다른 도구로도
    게시하면 우리 이력만으로는 실제 소모량을 알 수 없다. 정확한 한도 관리가 필요해지면
    그 엔드포인트를 호출하도록 바꿔야 한다.

## 출처

- [Create an Instagram App (이용 사례 선택·Instagram 제품 추가·토큰 생성 단계)](https://developers.facebook.com/docs/instagram-platform/create-an-instagram-app/)
  — 2026-07-29 추가 확인. "이용 사례 = Other, 앱 유형 = 비즈니스" 제약의 출처.
- [Instagram API with Instagram Login (개요)](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login)
  — "페이스북 페이지 불필요" 근거.
- [Instagram API with Instagram Login — Get Started](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started)
- [Access Token 참조 (수명·교환)](https://developers.facebook.com/docs/instagram-platform/reference/access_token/)
- [Content Publishing (권한·100건 한도)](https://developers.facebook.com/docs/instagram-platform/content-publishing)
- [Instagram API with Facebook Login — Get Started](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/get-started) (비교용)
