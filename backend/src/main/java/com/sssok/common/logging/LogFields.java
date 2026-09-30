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

    private LogFields() {
    }
}
