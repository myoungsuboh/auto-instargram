---
name: "관계형 DB 공통 네이밍/타입 표준 (Relational DB Conventions)"
description: "모든 관계형 DB에 동일 적용하는 범용(foundational) 네이밍·공통 컬럼·데이터 타입 단일 규격. 신규 테이블/컬럼을 설계하거나 약어 컬럼 등 레거시를 이주할 때, 갈린 네이밍을 통일할 때 읽는다. dialect 차이는 전용 스킬로, 논리삭제·감사는 `soft-delete-audit`으로 위임. 키워드: snake_case, primary key, foreign key, naming, common columns, created_at, deleted_at, data type, decimal."
---

# 관계형 DB 공통 네이밍/타입 표준 (Relational DB Conventions)

**ID:** `SKL-DB-COMMON-CONVENTIONS`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 모든 관계형 DB에 동일 적용하는 범용(foundational) 네이밍·공통 컬럼·데이터 타입 단일 규격. 신규 테이블/컬럼을 설계하거나 약어 컬럼 등 레거시를 이주할 때, 갈린 네이밍을 통일할 때 읽는다. dialect 차이는 전용 스킬로, 논리삭제·감사는 `soft-delete-audit`으로 위임. 키워드: snake_case, primary key, foreign key, naming, common columns, created_at, deleted_at, data type, decimal.

---

## 지시사항 (Instructions)

1. 네이밍은 한 규격으로: 테이블명은 복수형 snake_case, 컬럼명은 단수형 snake_case. 접두사(TB_, tb_ 등)·대문자·약어를 섞지 않는다.
2. 키는 일관된 형식으로: PK는 id, FK는 참조테이블단수_id 형식으로 통일한다. 의미가 바뀔 수 있는 자연키(이메일·사번)를 PK로 쓰지 않는다.
3. 이름은 의미를 드러낸다: 약어 컬럼명을 금지하고 전체 단어를 쓴다: 이름만 보고 무엇인지 알 수 있어야 한다.
4. 공통 컬럼을 강제한다: 모든 테이블에 생성/수정 시각·작성자 등 공통 컬럼을 둔다. 감사(audit) 정보 없이 운영하지 않는다.
5. 삭제는 논리 삭제가 기본: 물리 삭제 대신 삭제 시각(deleted_at)을 채우고, 조회는 항상 '살아있는 행'(deleted_at IS NULL)만 본다. 감사 추적과 참조 무결성을 보존한다(패턴 상세는 soft-delete-audit).
6. 타입은 안전한 기본값으로: 금액은 부동소수점이 아닌 고정소수점, 참/거짓은 전용 불리언 타입, 키는 충분히 큰 정수형/식별자 타입을 쓴다.
7. 제품·도구 종속은 위임한다: 페이징·UPSERT·자동 증가 키 같은 dialect 차이, 매퍼/ORM 매핑 같은 도구 사용법은 본문이 아니라 각 전용 스킬·부록으로 미룬다.

## 태그

`snake_case` `primary key` `foreign key` `naming` `common columns` `created_at` `deleted_at` `data type` `decimal` `varchar` `updated_at` `db-common-conventions` `db` `ai-recommended`
