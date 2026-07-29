package com.autoinstagram.backend.common.util;

import java.util.regex.Pattern;

/**
 * POL-05 구현: "모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함"
 * (1_spack.md §3 Policies, 적용 대상 ENT-03 SecurityCredential)
 *
 * <p>2_ddd.md AGG-03 불변식 {@code token string must be masked in logs} 도 같은 요구다.
 *
 * <p>토큰이 로그로 새는 대표 경로는 셋이다:
 * <ol>
 *   <li>{@code log.info("token=" + token)} 처럼 직접 찍는 경우 → {@link #mask(String)} 로 감싼다</li>
 *   <li>엔티티 {@code toString()} 이 필드를 전부 출력하는 경우 → SecurityCredential 은 toString 을 직접 구현한다</li>
 *   <li>예외 메시지에 원문이 섞여 들어오는 경우 → {@link #scrub(String)} 로 걸러낸다</li>
 * </ol>
 */
public final class TokenMasker {

    /** 앞 4자만 남긴다. 너무 짧으면 아예 아무 것도 남기지 않는다. */
    private static final int VISIBLE_PREFIX = 4;
    private static final int MIN_LENGTH_TO_SHOW_PREFIX = 12;
    private static final String MASK = "***";

    /**
     * Meta/Instagram 액세스 토큰 형태(EAA... 로 시작하는 긴 문자열)와
     * JWT 형태(x.y.z)를 임의 문자열 안에서 찾아내기 위한 패턴.
     * 화이트리스트 방식이 불가능한 영역(자유 형식 예외 메시지)이라 패턴 기반으로 훑는다.
     */
    private static final Pattern LIKELY_TOKEN = Pattern.compile(
            "(EAA[A-Za-z0-9_\\-]{10,})"                                  // Meta long-lived token
                    + "|([A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,})"  // JWT
    );

    private TokenMasker() {
    }

    /**
     * 토큰 값을 로그·에러에 안전한 형태로 바꾼다.
     *
     * <p>길이는 남긴다 — 장애 조사 시 "토큰이 비었는지 / 잘렸는지" 를 구분해야 하는데
     * 그건 원문 없이도 판단할 수 있어야 한다.
     */
    public static String mask(String token) {
        if (token == null) {
            return "<null>";
        }
        if (token.isEmpty()) {
            return "<empty>";
        }
        if (token.length() < MIN_LENGTH_TO_SHOW_PREFIX) {
            // 짧은 값은 앞 4자만으로도 상당 부분이 드러나므로 전부 가린다
            return MASK + "(len=" + token.length() + ")";
        }
        return token.substring(0, VISIBLE_PREFIX) + MASK + "(len=" + token.length() + ")";
    }

    /**
     * 자유 형식 문자열(외부 API 에러 본문, 예외 메시지 등)에서 토큰처럼 보이는 부분을 지운다.
     * 외부 응답을 그대로 로그에 남기면 그 안에 토큰이 되돌아와 있을 수 있다.
     */
    public static String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return LIKELY_TOKEN.matcher(text).replaceAll("<redacted-token>");
    }
}
