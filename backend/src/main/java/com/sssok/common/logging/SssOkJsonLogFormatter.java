package com.sssok.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.sssok.common.version.VersionInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * 로그 한 줄을 팀이 합의한 JSON 규격으로 직렬화한다.
 *
 * <p>Spring Boot 내장 포맷(ECS·GELF·Logstash)을 쓰지 않은 이유는 필드 이름 때문이다. ECS 는
 * {@code @timestamp}·{@code log.level} 처럼 자기 규격을 강제하는데, 우리는 프론트·Nginx·백엔드가
 * 같은 이름을 쓰기로 정해 둔 상태다. 이름을 맞추는 쪽이 Loki 질의문을 단순하게 만든다.
 *
 * <p>버전 세 값은 기동 중 한 번만 읽어 모든 줄에 박는다. 어느 배포본이 낸 로그인지 줄 단위로
 * 판별할 수 있어야, 배포 직후 오류가 새 버전에서 난 것인지 구버전 잔여 트래픽인지 가려낼 수 있다.
 * 이 포맷터는 스프링 컨텍스트보다 먼저 만들어지므로 {@code VersionInfo} 빈을 주입받을 수 없어,
 * 같은 출처(build-info → 환경변수)를 직접 읽는다.
 *
 * <p>등록은 {@code logging.structured.format.console} 프로퍼티로 한다 (dev·prod 만).
 */
public class SssOkJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final String BUILD_INFO_PATH = "META-INF/build-info.properties";
    private static final String BUILD_INFO_VERSION_KEY = "build.version";
    private static final String DEFAULT_SERVICE = "sssok-backend";
    private static final String DEFAULT_ENVIRONMENT = "local";

    private final String service;
    private final String environment;
    private final VersionInfo version;
    private final JsonWriter<ILoggingEvent> writer;

    public SssOkJsonLogFormatter(Environment environment) {
        this.service = environment.getProperty("log.service", DEFAULT_SERVICE);
        this.environment = resolveEnvironment(environment);
        this.version = resolveVersion(environment);
        this.writer = JsonWriter.<ILoggingEvent>of(this::members).withNewLineAtEnd();
    }

    @Override
    public String format(ILoggingEvent event) {
        return writer.writeToString(event);
    }

    // 필드 순서는 사람이 터미널에서 읽을 때를 기준으로 잡았다 — 언제·얼마나 심각한지, 어느 요청인지,
    // 무슨 일인지, 어느 배포본인지 순서다. JSON 자체는 순서에 의미가 없다.
    private void members(JsonWriter.Members<ILoggingEvent> members) {
        members.add(LogFields.TIMESTAMP, event -> event.getInstant().toString());
        members.add(LogFields.LEVEL, event -> event.getLevel().toString());
        members.add(LogFields.SERVICE, service);
        members.add(LogFields.ENVIRONMENT, environment);

        // 요청 스레드에서만 차는 값. 배치·워커 스레드 로그에는 없으므로 비어 있으면 통째로 뺀다.
        members.add(LogFields.REQUEST_ID, event -> mdc(event, LogFields.REQUEST_ID)).whenHasLength();
        members.add(LogFields.METHOD, event -> mdc(event, LogFields.METHOD)).whenHasLength();
        members.add(LogFields.PATH, event -> mdc(event, LogFields.PATH)).whenHasLength();
        members.add(LogFields.STATUS, event -> number(event, LogFields.STATUS)).whenNotNull();
        members.add(LogFields.ERROR_CODE, event -> mdc(event, LogFields.ERROR_CODE)).whenHasLength();
        members.add(LogFields.DURATION_MS, event -> number(event, LogFields.DURATION_MS)).whenNotNull();

        members.add(LogFields.MESSAGE, event -> SensitiveValueMasker.mask(event.getFormattedMessage()));
        members.add("logger", ILoggingEvent::getLoggerName);
        members.add("thread", ILoggingEvent::getThreadName);

        members.add("exception", event -> throwableClass(event.getThrowableProxy())).whenHasLength();
        members.add("stackTrace", event -> stackTrace(event.getThrowableProxy())).whenHasLength();

        members.add(LogFields.RELEASE_VERSION, version.releaseVersion());
        members.add(LogFields.BACKEND_VERSION, version.backendVersion());
        members.add(LogFields.GIT_SHA, version.gitSha());
    }

    private String mdc(ILoggingEvent event, String key) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null) {
            return null;
        }
        return SensitiveValueMasker.mask(mdc.get(key));
    }

    // status 와 durationMs 만 문자열이 아닌 숫자로 내보낸다. Loki·Grafana 에서 `status >= 500`
    // 이나 `durationMs > 1000` 같은 비교를 하려면 숫자여야 한다. MDC 는 문자열만 담으므로
    // 여기서 되돌린다.
    private Integer number(ILoggingEvent event, String key) {
        String raw = mdc(event, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String throwableClass(IThrowableProxy throwable) {
        return throwable == null ? null : throwable.getClassName();
    }

    private String stackTrace(IThrowableProxy throwable) {
        if (throwable == null) {
            return null;
        }
        return SensitiveValueMasker.mask(ThrowableProxyUtil.asString(throwable));
    }

    // 활성 프로파일이 곧 환경이다. 여러 개가 켜져 있으면 첫 번째를 쓴다 (local | dev | prod).
    private String resolveEnvironment(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0) {
            return active[0];
        }
        return environment.getProperty("spring.profiles.default", DEFAULT_ENVIRONMENT);
    }

    // VersionConfig 와 같은 규칙: 백엔드 버전은 JAR 의 build-info 가 1순위, 없으면 주입된 환경변수.
    // VersionConfig 는 BuildProperties 빈으로 읽지만 여기는 컨텍스트 밖이라 리소스를 직접 연다.
    private VersionInfo resolveVersion(Environment environment) {
        String buildInfoVersion = buildInfoVersion();
        String backendVersion = buildInfoVersion != null
            ? buildInfoVersion
            : property(environment, "release.backend-version");
        return new VersionInfo(
            property(environment, "release.version"),
            backendVersion,
            property(environment, "release.git-sha"));
    }

    private String property(Environment environment, String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? VersionInfo.UNKNOWN : value;
    }

    private String buildInfoVersion() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(BUILD_INFO_PATH)) {
            if (in == null) {
                return null;
            }
            Properties buildInfo = new Properties();
            buildInfo.load(in);
            return buildInfo.getProperty(BUILD_INFO_VERSION_KEY);
        } catch (IOException e) {
            // 버전 한 줄 때문에 로깅 초기화를 막지 않는다. 값이 없으면 unknown 으로 떨어진다.
            return null;
        }
    }
}
