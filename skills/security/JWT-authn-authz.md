---
name: "인증(AuthN) & 인가(AuthZ) 패턴"
description: "JWT·OAuth 2.0·RBAC/ABAC 기반 인증·인가 구현의 보안 모범 사례. 토큰 저장·갱신 전략·권한 검증·세션 관리를 정하거나 로그인/인가 코드를 만들 때 읽는다. 키워드: JWT, httpOnly, refresh_token, access_token, RBAC, Authorization, Bearer, OAuth, PKCE."
---

# 인증(AuthN) & 인가(AuthZ) 패턴

**ID:** `SKL-AUTHN-AUTHZ`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** JWT·OAuth 2.0·RBAC/ABAC 기반 인증·인가 구현의 보안 모범 사례. 토큰 저장·갱신 전략·권한 검증·세션 관리를 정하거나 로그인/인가 코드를 만들 때 읽는다. 키워드: JWT, httpOnly, refresh_token, access_token, RBAC, Authorization, Bearer, OAuth, PKCE.

---

## 지시사항 (Instructions)

1. JWT는 httpOnly·Secure·SameSite=Strict 쿠키에 저장하고, localStorage/sessionStorage에 저장하지 않는다.
2. Access Token 만료 시간은 15분 이하로 설정하고, Refresh Token으로 자동 갱신한다.
3. 권한 검증은 반드시 서버 측에서 수행하고, 클라이언트 조건(v-if)으로만 UI를 숨기는 방식에 의존하지 않는다.
4. OAuth 2.0 Authorization Code Flow + PKCE를 사용하고, Implicit Flow를 사용하지 않는다.
5. 실패한 로그인 시도를 계정별·IP별로 제한(Rate Limit)하고, 임계치 초과 시 CAPTCHA 또는 계정 잠금을 적용한다.

## 태그

`JWT` `httpOnly` `refresh_token` `access_token` `RBAC` `Authorization` `Bearer` `OAuth` `PKCE` `authn-authz` `security` `ai-recommended`
