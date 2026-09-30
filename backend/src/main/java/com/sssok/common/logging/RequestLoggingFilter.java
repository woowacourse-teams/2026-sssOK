package com.sssok.common.logging;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나에 식별자를 붙이고, 그 요청에서 나온 모든 로그가 같은 식별자를 갖게 한다.
 *
 * <p>장애 제보는 대개 "몇 시쯤 안 됐어요" 한 줄이다. 응답 헤더로 돌려준 요청 ID 를 받아 적으면
 * 그 값 하나로 해당 요청의 로그만 뽑아낼 수 있다. 그래서 ID 는 성공·실패를 가리지 않고 모든
 * 응답에 싣는다.
 *
 * <p>ID 는 앱이 만든다. Nginx 는 받은 헤더를 그대로 넘기기만 한다 — 생성 지점을 하나로 둬야
 * 형식 검증 규칙도 한 곳에만 있게 된다.
 *
 * <p>필터 체인 맨 앞에 둔다. 뒤쪽 필터나 인터셉터가 남기는 로그에도 ID 가 붙어야 하기 때문이다.
 *
 * <p>MDC 는 요청 스레드에만 채운다 — 비동기 실행기에 {@code TaskDecorator} 를 걸어 요청 ID 를
 * 워커로 넘기지 않는다. 이 앱의 비동기 작업(썸네일 생성·zip 압축·스토리지 정리)은 요청보다 오래
 * 살고, 요청 하나가 여러 건을 던지고, 실패하면 나중에 다시 실행된다. 그 로그에 요청 ID 를 달면
 * "이 요청은 200 으로 끝났는데 같은 ID 의 로그가 10분 뒤에 또 난다" 가 되어, ID 하나로 요청
 * 하나를 집어내는 추적이 오히려 흐려진다. 작업 쪽 추적은 작업 자신의 식별자(jobId·mediaId)로 한다.
 * 이 결정이 조용히 깨지지 않도록 {@code MdcIsolationTest} 가 누수 여부를 직접 확인한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    // 소문자 UUID 36자만 받는다. 상한이 없으면 클라이언트가 보낸 긴 문자열이 그대로 로그 필드에
    // 실려 수집 비용이 되고, 대소문자를 섞어 받으면 같은 요청이 다른 값으로 검색된다.
    private static final Pattern REQUEST_ID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // 배포 헬스체크와 모니터링 수집기가 반복 호출하는 경로. 접근 로그로 남기면 실제 트래픽이 묻힌다.
    // 지금 실제로 걸리는 건 /health 다 — 배포 헬스체크가 수 초마다 때린다.
    //
    // /actuator 는 지금은 이 필터에 닿지 않는다. management.server.port 를 8081 로 분리해 뒀고,
    // 그 포트는 자기 자신의 필터 체인을 쓰는 별도 컨텍스트라 여기 등록된 필터가 걸리지 않는다.
    // 그래도 목록에 남기는 이유는 두 가지다.
    //   - 관리 포트를 다시 8080 으로 합치면(운영 편의상 충분히 있을 수 있다) 그날부터 Prometheus 의
    //     15초 주기 수집이 하루 약 5,760줄을 쌓는다. 그때 이 목록이 이미 막고 있어야 한다.
    //   - 공개 포트로 /actuator 를 긁어 보는 스캐너의 404 가 로그를 채우지 않는다.
    //
    // 하위 경로까지 통째로 빼는 이유는, 노출 엔드포인트(health·info·prometheus)가 늘거나 health
    // 그룹(liveness·readiness)이 추가될 때마다 이 목록을 따라 고치게 두지 않기 위해서다.
    //
    // 목록으로 두는 이유는 값이 이 파일 밖에도 같이 적혀 있기 때문이다 — 바꿀 때 아래 세 곳을
    // 함께 본다.
    //   - docker-compose.{dev,prod}.yml 의 app healthcheck (지금 /health)
    //   - .github/workflows/deploy-*.yml 의 배포 후 확인 요청 (지금 /health)
    //   - monitoring/prometheus/*.yml 의 metrics_path (지금 /actuator/prometheus)
    // 모니터링 쪽이 헬스체크 경로를 /actuator/health 로 옮겨도 여기 목록은 그대로 덮는다.
    private static final List<String> ACCESS_LOG_EXCLUDED_PATHS = List.of("/health", "/actuator");

    // 톰캣이 끊긴 소켓에 쓰다 던지는 예외. 클래스 이름으로만 보는 이유는 이 파일이 서블릿 컨테이너
    // 구현에 직접 의존하지 않게 하기 위해서다.
    private static final String CLIENT_ABORT_EXCEPTION = "ClientAbortException";

    // 연결이 끊겼다고 확신할 수 있는 메시지만 모았다. 여기에 없는 IOException 은 디스크·업스트림
    // 장애일 수 있으므로 클라이언트 탓으로 돌리지 않는다.
    private static final List<String> CLIENT_DISCONNECT_MESSAGES = List.of(
        "broken pipe",
        "connection reset",
        "connection reset by peer",
        "an existing connection was forcibly closed",
        "an established connection was aborted");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(LogFields.REQUEST_ID, requestId);
        MDC.put(LogFields.METHOD, request.getMethod());
        MDC.put(LogFields.PATH, request.getRequestURI());
        // 응답이 커밋되기 전에 박아야 한다. 예외로 빠져나가도 헤더는 이미 붙어 있다.
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                // SSE·zip 스트리밍은 여기서 아직 안 끝났다. 이 시점의 상태 코드는 최종값이 아니므로
                // 그대로 적으면 실패한 연결이 200 으로 남는다. 완료 시점을 따로 듣는다.
                logAsyncStarted(request, response, requestId, startedAt);
            } else {
                MDC.put(LogFields.STATUS, String.valueOf(response.getStatus()));
                logAccess(request, response, startedAt);
            }
            // 컨테이너가 이 스레드를 다음 요청에 재사용하므로, 비동기든 아니든 반드시 비운다.
            clearMdc();
        }
    }

    // 형식에 맞지 않으면 버리고 새로 발급한다. 거절(400) 하지 않는 이유는, 추적용 값 하나 때문에
    // 정상 요청을 실패시킬 이유가 없기 때문이다.
    private String resolveRequestId(HttpServletRequest request) {
        String given = request.getHeader(REQUEST_ID_HEADER);
        if (given != null && REQUEST_ID_PATTERN.matcher(given).matches()) {
            return given;
        }
        return UUID.randomUUID().toString();
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        int status = response.getStatus();
        if (isExcludedFromAccessLog(request.getRequestURI()) && !isServerError(status)) {
            return;
        }
        MDC.put(LogFields.DURATION_MS, String.valueOf(elapsedMs(startedAt)));
        log.info("{} {} {} ({}ms)", request.getMethod(), request.getRequestURI(), status, elapsedMs(startedAt));
    }

    private boolean isExcludedFromAccessLog(String path) {
        return ACCESS_LOG_EXCLUDED_PATHS.stream()
            .anyMatch(excluded -> excluded.equals(path) || path.startsWith(excluded + "/"));
    }

    // 제외 경로라도 5xx 는 남긴다. 헬스체크가 실패하는 중이라면 그게 바로 보고 싶은 줄이고,
    // Prometheus 수집이 500 으로 끊기면 지표가 비는 이유가 여기서만 드러난다.
    private boolean isServerError(int status) {
        return status >= 500;
    }

    // 비동기 요청은 시작과 끝을 따로 남긴다. 한 줄만 남기면 오래 열려 있는 SSE 연결이 로그에서
    // 통째로 사라져(요청은 들어왔는데 기록이 없어) 추적이 끊긴다.
    //
    // 완료 콜백은 요청 스레드가 아닌 곳에서 불려 MDC 가 비어 있다. 요청 ID 를 값으로 넘겨받아
    // 그때 다시 채워 넣어야 시작 줄과 끝 줄이 같은 ID 로 묶인다.
    private void logAsyncStarted(
        HttpServletRequest request, HttpServletResponse response, String requestId, long startedAt) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        log.info("{} {} 비동기 응답 시작", method, path);
        request.getAsyncContext().addListener(new AsyncListener() {

            @Override
            public void onComplete(AsyncEvent event) {
                logAsyncEnd("비동기 응답 종료", null);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                logAsyncEnd("비동기 응답 타임아웃", null);
            }

            @Override
            public void onError(AsyncEvent event) {
                logAsyncEnd("비동기 응답 오류", event.getThrowable());
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // 재디스패치는 이 앱에서 쓰지 않는다.
            }

            private void logAsyncEnd(String what, Throwable error) {
                MDC.put(LogFields.REQUEST_ID, requestId);
                MDC.put(LogFields.METHOD, method);
                MDC.put(LogFields.PATH, path);
                // 비동기 종료 줄에도 status 를 실어야 "SSE 가 500 으로 끊긴 건수" 같은 질의가 선다.
                // 동기 요청과 달리 이 값은 완료 콜백 시점에야 확정되므로 여기서 읽는다.
                MDC.put(LogFields.STATUS, String.valueOf(response.getStatus()));
                MDC.put(LogFields.DURATION_MS, String.valueOf(elapsedMs(startedAt)));
                try {
                    // 브라우저가 SSE 탭을 닫으면 IOException(Broken pipe)이 난다. 정상 종료라
                    // WARN 으로 남기면 탭을 닫을 때마다 운영 로그가 쌓인다. 스택트레이스도 뺀다.
                    if (error != null && !isClientDisconnect(error)) {
                        log.warn("{} {} {} ({}ms)", method, path, what, elapsedMs(startedAt), error);
                        return;
                    }
                    if (error != null) {
                        log.debug("{} {} 클라이언트가 연결을 끊었습니다 ({}ms)", method, path, elapsedMs(startedAt));
                        return;
                    }
                    log.info("{} {} {} ({}ms)", method, path, what, elapsedMs(startedAt));
                } finally {
                    clearMdc();
                }
            }
        });
    }

    // 끊긴 연결에 쓰다 나는 IOException 은 우리가 손쓸 일이 없는 정상 종료다.
    //
    // 다만 IOException 이라는 것만으로 연결 종료라고 볼 수는 없다. 디스크 오류나 업스트림 장애도
    // 같은 타입으로 올라오는데, 그걸 DEBUG 로 묻으면 정작 우리가 고쳐야 할 장애가 로그에서 사라진다.
    // 그래서 연결 종료라고 확신할 수 있는 예외 타입·메시지만 골라내고, 나머지는 WARN 으로 넘긴다.
    private boolean isClientDisconnect(Throwable error) {
        for (Throwable cause = error; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof IOException && isDisconnectSignal(cause)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDisconnectSignal(Throwable cause) {
        if (CLIENT_ABORT_EXCEPTION.equals(cause.getClass().getSimpleName())) {
            return true;
        }
        String message = cause.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return CLIENT_DISCONNECT_MESSAGES.stream().anyMatch(normalized::contains);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // 우리가 넣은 키만 지운다. MDC.clear() 는 다른 코드가 넣어 둔 값까지 날린다.
    private void clearMdc() {
        MDC.remove(LogFields.REQUEST_ID);
        MDC.remove(LogFields.METHOD);
        MDC.remove(LogFields.PATH);
        MDC.remove(LogFields.STATUS);
        MDC.remove(LogFields.ERROR_CODE);
        MDC.remove(LogFields.DURATION_MS);
    }
}
