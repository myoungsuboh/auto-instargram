---
name: "OWASP Top 10 보안 가이드"
description: "OWASP Top 10(2021) 기준의 주요 취약점을 예방하는 방어적 코딩 표준: Injection, XSS, 인증 결함, 취약한 의존성 등을 다룬다. 새 기능을 설계·구현하거나 외부 입력·인증·권한·데이터 노출을 다룰 때, 보안 점검·코드 리뷰 시 읽는다. 키워드: sanitize, escape, parameterized, IDOR, XSS, injection, npm audit, Content-Security-Policy."
---

# OWASP Top 10 보안 가이드

**ID:** `SKL-OWASP-TOP10`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** OWASP Top 10(2021) 기준의 주요 취약점을 예방하는 방어적 코딩 표준: Injection, XSS, 인증 결함, 취약한 의존성 등을 다룬다. 새 기능을 설계·구현하거나 외부 입력·인증·권한·데이터 노출을 다룰 때, 보안 점검·코드 리뷰 시 읽는다. 키워드: sanitize, escape, parameterized, IDOR, XSS, injection, npm audit, Content-Security-Policy.

---

## 지시사항 (Instructions)

1. 모든 외부 입력(사용자·API·파일)은 신뢰하지 않고 화이트리스트 검증 또는 파라미터화 쿼리를 사용한다.
2. HTML 출력 시 사용자 입력을 반드시 이스케이프하고, innerHTML에 미검증 데이터를 삽입하지 않는다.
3. IDOR 방지를 위해 리소스 접근 시 소유권·권한을 서버에서 재검증한다.
4. 민감한 데이터(비밀번호·토큰·PII)는 응답 바디·URL·로그에 노출하지 않는다.
5. 의존성은 정기적으로 취약점을 스캔하고(npm audit / pip-audit), CRITICAL 취약점은 즉시 패치한다.

## 태그

`sanitize` `escape` `parameterized` `IDOR` `XSS` `injection` `npm audit` `Content-Security-Policy` `owasp-top10` `security` `ai-recommended`
