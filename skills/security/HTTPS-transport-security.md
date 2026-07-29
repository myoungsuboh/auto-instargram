---
name: "전송 보안 표준: TLS·HTTPS·보안 헤더"
description: "전송 계층의 도청·변조·클릭재킹을 막는 범용(foundational) 표준. HTTPS 강제, TLS 최소 버전·암호 스위트, HSTS·CSP·보안 헤더, CORS 화이트리스트, 민감 응답 캐시 금지를 다룬다. 서버·API의 헤더·TLS·CORS를 구성·점검하거나 HTTPS 전환을 정비할 때 읽는다."
---

# 전송 보안 표준: TLS·HTTPS·보안 헤더

**ID:** `SKL-TRANSPORT-SECURITY`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 전송 계층의 도청·변조·클릭재킹을 막는 범용(foundational) 표준. HTTPS 강제, TLS 최소 버전·암호 스위트, HSTS·CSP·보안 헤더, CORS 화이트리스트, 민감 응답 캐시 금지를 다룬다. 서버·API의 헤더·TLS·CORS를 구성·점검하거나 HTTPS 전환을 정비할 때 읽는다.

---

## 지시사항 (Instructions)

1. 평문 전송을 신뢰하지 않는다: 네트워크 경로는 도청·변조될 수 있다고 가정한다. 모든 트래픽은 HTTPS로만 허용하고, HTTP 요청은 영구 리다이렉트(301)로 HTTPS로 전환한다.
2. HSTS로 평문 재접속을 차단한다: 첫 요청 이후 브라우저가 평문으로 되돌아가지 않도록 HSTS를 강제한다. 리다이렉트만으로는 최초 평문 요청이 노출되므로 HSTS가 필수다.
3. 기본 거부, 명시 허용(default-deny): CSP·CORS는 '모두 막고 필요한 출처만 연다'를 기본으로 한다. 와일드카드 허용은 신뢰 경계를 무너뜨린다.
4. TLS는 안전한 버전·암호만: 취약한 프로토콜·암호 스위트로의 다운그레이드를 막는다. 최소 버전 이상만 허용한다.
5. 민감 응답은 캐시에 남기지 않는다: 인증·개인정보가 담긴 응답은 프록시·브라우저 캐시에 잔류하지 않게 한다.
6. 헤더·정책은 한곳에서 일괄 적용: 보안 헤더와 CORS 정책을 엔드포인트마다 흩뿌리지 말고 공통 진입 지점(미들웨어/필터/게이트웨이)에서 일관되게 건다.
7. 설정은 코드가 아닌 환경으로: 허용 출처·인증서 등 환경마다 다른 값은 환경 변수/설정으로 분리해, 개발 설정이 프로덕션으로 새지 않게 한다.

## 태그

`HTTPS` `TLS` `HSTS` `Content-Security-Policy` `X-Frame-Options` `CORS` `Strict-Transport-Security` `helmet` `transport-security` `security` `ai-recommended`
