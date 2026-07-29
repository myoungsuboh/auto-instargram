---
name: "Soft Delete & 감사 컬럼"
description: "논리 삭제·생성/수정/삭제 감사 컬럼·변경 이력의 범용(foundational) 표준: 물리 삭제 대신 `deleted_at`, 감사 컬럼 자동 갱신, 활성 레코드 기본 필터, 삭제 제외 조건부 유니크, 이력 보존. 삭제·감사 컬럼을 설계하거나 삭제 레코드와 유니크 제약이 충돌할 때 읽는다(컬럼 네이밍·타입은 `db-common-conventions`). 키워드: soft-delete, deleted_at, audit, history, partial unique, 이력 테이블."
---

# Soft Delete & 감사 컬럼

**ID:** `SKL-SOFT-DELETE-AUDIT`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 논리 삭제·생성/수정/삭제 감사 컬럼·변경 이력의 범용(foundational) 표준: 물리 삭제 대신 `deleted_at`, 감사 컬럼 자동 갱신, 활성 레코드 기본 필터, 삭제 제외 조건부 유니크, 이력 보존. 삭제·감사 컬럼을 설계하거나 삭제 레코드와 유니크 제약이 충돌할 때 읽는다(컬럼 네이밍·타입은 `db-common-conventions`). 키워드: soft-delete, deleted_at, audit, history, partial unique, 이력 테이블.

---

## 지시사항 (Instructions)

1. 물리 삭제 대신 논리 삭제: 비즈니스 데이터는 행을 지우지 않고 '삭제 시각' 표시로 논리 삭제한다. 영구 삭제는 별도 아카이빙/정리 프로세스로만 처리한다: 복구·감사 가능성을 남긴다.
2. 감사 컬럼은 모든 도메인 테이블 공통: 생성/수정/삭제의 '언제·누가'를 표준 컬럼(created_at/updated_at/deleted_at, created_by/updated_by/deleted_by)으로 모든 도메인 테이블에 둔다. 삭제 플래그는 nullable timestamp deleted_at(NULL=활성)을 쓴다: 불리언이 아니다. 컬럼 네이밍·타입 규약 자체는 db-common-conventions 스킬을 따른다.
3. 감사 컬럼은 자동 갱신: updated_at 같은 값은 수동 갱신에 의존하지 말고 DB 트리거 또는 ORM/애플리케이션 훅으로 자동 채운다: 일부 경로에서 빠지지 않게 한다.
4. 활성 레코드 기본 필터: '삭제 안 된 행만' 조건을 기본 조회에 포함하고, 가능하면 ORM 전역 스코프 등으로 자동 적용해 실수로 삭제 행이 노출되지 않게 한다.
5. 삭제 레코드를 제외한 유니크: 자연키(이메일 등) 유니크 제약은 '활성 레코드 한정'으로 건다: 삭제 후 같은 값으로 재등록할 때 충돌하지 않게 한다.
6. 변경 이력은 별도 테이블: 중요한 데이터의 변경 추적이 필요하면 이력 테이블(audit_log 또는 *_history 접미사)을 별도로 운영한다.
7. 무한 증식 방지: 논리 삭제만 하고 정리하지 않으면 테이블이 무한히 커진다. 보존 기간·아카이빙·정리 정책을 함께 정한다.

## 태그

`soft-delete` `deleted_at` `audit` `history` `partial unique` `이력 테이블` `created_at` `updated_at` `logical-delete` `soft-delete-audit` `db` `ai-recommended`
