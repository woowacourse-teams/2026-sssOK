package com.sssok.common.logging;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 로그로 나가는 문자열에서 민감한 값을 가린다.
 *
 * <p>1차 방어선은 "애초에 남기지 않는 것"이다 — 헤더·요청 본문·응답 본문을 통째로 찍는 코드를
 * 두지 않는다. 이 클래스는 그 규칙이 한 군데서 깨졌을 때를 대비한 2차 방어선이라,
 * 예외 메시지나 서드파티 라이브러리 로그처럼 우리가 문구를 통제하지 못하는 경로를 노린다.
 *
 * <p>적용 지점은 {@link SssOkJsonLogFormatter} 한 곳이다. 로그 메시지·MDC 값·스택트레이스가
 * JSON 으로 직렬화되기 직전에 통과한다. {@code local} 프로파일의 평문 로그는 대상이 아니다 —
 * 로컬 로그는 수집되지도 저장되지도 않고, 개발자가 토큰 원문을 봐야 할 때가 있다.
 */
public final class SensitiveValueMasker {

    public static final String MASK = "***";

    // 민감한 값으로 간주하는 키. 로그에 남기면 안 되는 항목을 이름 기준으로 모아 둔다.
    private static final String SENSITIVE_KEYS =
        "password|passwd|pwd|secret|secret[_-]?key|access[_-]?key|api[_-]?key|authorization|cookie|jwt|token";

    // R2 서명 URL 의 쿼리 파라미터. 이 값이 있으면 URL 하나로 객체를 그대로 받아갈 수 있다.
    private static final String SIGNATURE_PARAMS =
        "x-amz-signature|x-amz-credential|x-amz-security-token|signature|awsaccesskeyid";

    private static final List<Rule> RULES = List.of(
        // Authorization: Bearer <토큰>
        new Rule(Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/]+=*"), "$1" + MASK),
        // 헤더 이름 없이 본문에 박힌 JWT (header.payload.signature)
        new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*"), MASK),
        // 서명 URL 의 쿼리 파라미터 값
        new Rule(Pattern.compile("(?i)([?&](?:" + SIGNATURE_PARAMS + ")=)[^&\\s\"']*"), "$1" + MASK),
        // key=value / "key": "value" 형태로 노출된 비밀. JDBC URL 의 password 도 여기서 걸린다.
        new Rule(Pattern.compile("(?i)([\"']?(?:" + SENSITIVE_KEYS + ")[\"']?\\s*[:=]\\s*)[\"']?[^\"',;&\\s}]+"),
            "$1" + MASK)
    );

    private SensitiveValueMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = value;
        for (Rule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }
        return masked;
    }

    private record Rule(Pattern pattern, String replacement) {
    }
}
