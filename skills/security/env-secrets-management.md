---
name: "시크릿 관리 표준 (Secrets Management)"
description: "API 키·DB 비밀번호·인증서·토큰 같은 시크릿을 안전하게 다루는 범용(foundational) 표준. 하드코딩 금지·외부 주입, 커밋·이력 스캐닝, 정기 로테이션, 로그 마스킹, 환경별 분리를 다룬다. 시크릿을 새로 다루거나 설정 주입 방식을 정할 때, 커밋 전 노출을 막을 때 읽는다. 환경변수 주입 자체는 env-config 참고."
---

# 시크릿 관리 표준 (Secrets Management)

**ID:** `SKL-SECRETS-MANAGEMENT`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** API 키·DB 비밀번호·인증서·토큰 같은 시크릿을 안전하게 다루는 범용(foundational) 표준. 하드코딩 금지·외부 주입, 커밋·이력 스캐닝, 정기 로테이션, 로그 마스킹, 환경별 분리를 다룬다. 시크릿을 새로 다루거나 설정 주입 방식을 정할 때, 커밋 전 노출을 막을 때 읽는다. 환경변수 주입 자체는 env-config 참고.

---

## 지시사항 (Instructions)

1. 밖에 두고 주입받는다: 시크릿은 실행 환경이나 전용 매니저에서 런타임에 주입하고, 환경이 올라갈수록(로컬→공유→운영) 더 강한 통제를 가진 저장소를 쓴다.
2. 노출을 자동으로 막는다: 버전 관리에는 더미/예시 값만 두고, 커밋·CI·과거 이력을 스캔해 실제 시크릿이 새어 들어가지 못하게 한다.
3. 영구 자산으로 두지 않는다: 정기적으로, 유출 의심 시 즉시 교체(로테이션)하고, 시작 시 누락을 검증(fail-fast)하며, 로그·에러에 흔적을 남기지 않는다.

## 태그

`.env` `os.environ` `process.env` `SECRET` `API_KEY` `vault` `gitignore` `secrets-management` `security` `ai-recommended`
