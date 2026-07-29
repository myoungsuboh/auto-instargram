---
name: "입력 검증 & 데이터 새니타이징 (Security)"
description: "신뢰할 수 없는 입력을 서버 측에서 검증·새니타이즈해 Injection·XSS·Path Traversal을 막는 범용(foundational) 보안 표준으로, 화이트리스트·컨텍스트별 이스케이프/파라미터화·HTML 새니타이즈·파일 업로드 다중 검증을 다룬다(스택 무관). 입력을 받는 API·폼·파일 업로드를 만들거나 새니타이즈·이스케이프 로직을 고칠 때 읽는다. 키워드: sanitize, whitelist, escape, parameterize, injection, XSS, path traversal, file upload."
---

# 입력 검증 & 데이터 새니타이징 (Security)

**ID:** `SKL-INPUT-VALIDATION`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 신뢰할 수 없는 입력을 서버 측에서 검증·새니타이즈해 Injection·XSS·Path Traversal을 막는 범용(foundational) 보안 표준으로, 화이트리스트·컨텍스트별 이스케이프/파라미터화·HTML 새니타이즈·파일 업로드 다중 검증을 다룬다(스택 무관). 입력을 받는 API·폼·파일 업로드를 만들거나 새니타이즈·이스케이프 로직을 고칠 때 읽는다. 키워드: sanitize, whitelist, escape, parameterize, injection, XSS, path traversal, file upload.

---

## 지시사항 (Instructions)

1. 공통 토대(요약): 입력은 신뢰하지 않고, 강제는 서버 측에서, 제약은 선언적 스키마로, 실패는 거부(fail-closed). 상세는 validation-bean.
2. 화이트리스트 우선: '허용할 값'을 명시적으로 정의하고 나머지는 거부한다. '위험한 값'만 골라 막는 블랙리스트는 새 우회 패턴을 못 막는다.
3. 컨텍스트에 맞게 처리(이스케이프/파라미터화): 입력을 어디에 쓰느냐에 따라 방어가 다르다. 쿼리는 파라미터화(문자열 연결 금지), HTML 출력은 인코딩/이스케이프, 셸·파일 경로는 해당 컨텍스트 규칙으로 무력화한다. '검증했으니 그대로 써도 된다'는 위험하다.
4. 위험 출력은 새니타이즈: HTML 등 마크업을 허용해야 하면, 검증된 새니타이즈 도구로 허용 태그·속성만 통과시킨다. 자체 정규식으로 태그를 거르려 하지 않는다.
5. 파일 업로드는 다중 검증: 확장자·MIME 타입·실제 내용(매직 바이트)·크기를 모두 검증하고, 업로드 파일을 웹에서 직접 실행·제공되는 위치(웹 루트)에 저장하지 않는다.
6. Path Traversal 차단: 파일 경로/명에 입력을 결합할 때는 정규화 후 허용 디렉터리 안에 있는지 확인한다.

## 태그

`sanitize` `whitelist` `escape` `parameterize` `injection` `XSS` `path traversal` `file upload` `validate` `schema` `pydantic` `zod` `DOMPurify` `bleach` `input-validation` `security` `ai-recommended`
