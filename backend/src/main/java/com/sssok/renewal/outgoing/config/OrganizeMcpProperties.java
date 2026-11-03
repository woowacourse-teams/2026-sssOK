package com.sssok.renewal.outgoing.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gallery.organization.mcp")
public record OrganizeMcpProperties(String serverId, URI url, String token, Set<String> exposedTools,
                                   Duration timeout) {
    private static final String DEFAULT_SERVER_ID = "gallery";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    public OrganizeMcpProperties {
        serverId = serverId == null ? DEFAULT_SERVER_ID : serverId;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        exposedTools = exposedTools == null ? Set.of() : Set.copyOf(exposedTools);
        if (serverId.isBlank() || url == null || isBlank(token) || hasBlankName(exposedTools)
            || isInvalidTimeout(timeout)) {
            throw new IllegalArgumentException("사진 정리 MCP 설정을 확인해주세요");
        }
        if (!isAllowedUrl(url)) {
            throw new IllegalArgumentException("MCP 주소는 HTTPS 또는 로컬 HTTP여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "OrganizeMcpProperties[serverId=" + serverId + ", token=REDACTED]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasBlankName(Set<String> names) {
        return names.stream().anyMatch(String::isBlank);
    }

    private static boolean isInvalidTimeout(Duration timeout) {
        return timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0;
    }

    private static boolean isAllowedUrl(URI url) {
        boolean hasForbiddenPart = url.getHost() == null || url.getUserInfo() != null
            || url.getQuery() != null || url.getFragment() != null;
        return !hasForbiddenPart && (isHttps(url) || isLocalHttp(url));
    }

    private static boolean isHttps(URI url) {
        return "https".equals(url.getScheme());
    }

    private static boolean isLocalHttp(URI url) {
        return "http".equals(url.getScheme())
            && ("localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost()));
    }
}
