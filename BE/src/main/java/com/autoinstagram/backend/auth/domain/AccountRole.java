package com.autoinstagram.backend.auth.domain;

/**
 * 1_spack.md 의 {@code required_roles} 값을 코드로 옮긴 것.
 *
 * <p>명세는 소문자 스네이크(`system_admin`, `system_operator`)로 적혀 있으나
 * Java enum 관례와 DB CHECK 제약(V2 마이그레이션)에 맞춰 대문자로 저장하고,
 * 명세 표기와의 대응은 {@link #getSpecName()} 으로 명시적으로 남긴다.
 *
 * <p>Spring Security 권한 문자열은 {@code ROLE_} 접두사 관례를 따른다.
 */
public enum AccountRole {

    /**
     * 명세 {@code system_admin}.
     * API-05 (POST /api/v1/tokens/refresh) 는 이 권한만 허용한다.
     */
    SYSTEM_ADMIN("system_admin"),

    /**
     * 명세 {@code system_operator}.
     * API-01~04 는 운영자·관리자 모두 허용한다.
     */
    SYSTEM_OPERATOR("system_operator");

    private final String specName;

    AccountRole(String specName) {
        this.specName = specName;
    }

    /** 1_spack.md 에 적힌 원래 표기. 화면·문서와 대조할 때 쓴다. */
    public String getSpecName() {
        return specName;
    }

    /** Spring Security 가 기대하는 권한 문자열. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
