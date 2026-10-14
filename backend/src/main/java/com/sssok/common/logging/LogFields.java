package com.sssok.common.logging;

/**
 * 구조화 로그의 공통 필드 이름.
 *
 * <p>여기 있는 값은 그대로 JSON 키가 되고, Loki 질의문이 이 이름을 직접 참조한다.
 * 이름을 바꾸면 이미 저장된 로그와 대시보드 질의가 어긋나므로 신중히 고친다.
 *
 * <p>{@link #REQUEST_ID} 이하 다섯 개는 MDC 키이기도 하다. 요청 스레드에서만 값이 차고,
 * 요청이 끝나면 {@link RequestLoggingFilter} 가 지운다.
 */
public final class LogFields {

    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    public static final String MESSAGE = "message";
    public static final String RELEASE_VERSION = "releaseVersion";
    public static final String BACKEND_VERSION = "backendVersion";
    public static final String GIT_SHA = "gitSha";

    public static final String REQUEST_ID = "requestId";
    public static final String METHOD = "method";
    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String ERROR_CODE = "errorCode";

    // 앱이 요청을 처리한 시간. 단위가 밀리초라는 걸 이름에 박아 둔 이유는, 같은 요청을 nginx 가
    // durationSeconds(초, 소수점)로 남기기 때문이다. 두 값을 한 대시보드에 올릴 때 1,000배 차이가
    // 나므로 이름만 보고 단위를 알 수 있어야 한다. nginx 쪽은 요청이 nginx 에 들어온 순간부터
    // 응답을 다 쓸 때까지라, 이 값보다 항상 조금 크다.
    public static final String DURATION_MS = "durationMs";

    private LogFields() {
    }
}
