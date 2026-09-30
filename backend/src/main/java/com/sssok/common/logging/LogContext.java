package com.sssok.common.logging;

import org.slf4j.MDC;

/**
 * 응답 결과를 MDC 에 적어, 같은 요청에서 이어 남기는 로그에 붙게 한다.
 *
 * <p>{@link RequestLoggingFilter} 는 요청 진입 시점에 method·path 까지만 채울 수 있다.
 * 상태 코드와 에러 코드는 예외 처리기가 응답을 정한 뒤에야 알 수 있어, 그 지점에서 채워 넣는다.
 *
 * <p>여기 넣은 값은 {@code RequestLoggingFilter} 가 요청 끝에서 지운다.
 */
public final class LogContext {

    private LogContext() {
    }

    public static void putResponse(int status, String errorCode) {
        putStatus(status);
        MDC.put(LogFields.ERROR_CODE, errorCode);
    }

    // 에러 코드 없이 상태만 정해지는 응답(본문 없는 406 등)용.
    public static void putStatus(int status) {
        MDC.put(LogFields.STATUS, String.valueOf(status));
    }
}
