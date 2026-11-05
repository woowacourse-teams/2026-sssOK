package com.sssok.renewal.domain.auth;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

// 웹 출처(scheme://host[:port])를 비교 가능한 한 가지 문자열로 정규화한다. 형식이 잘못되면 IllegalArgumentException을 던진다.
public final class WebOrigin {

    private WebOrigin() {
    }

    // Origin 헤더 형식이다. 경로·쿼리·fragment·사용자 정보가 있으면 거절한다. 문자열 "null"도 거절한다.
    public static String parseOrigin(String value) {
        URI uri = parse(value);
        if (!uri.getRawPath().isEmpty() || uri.getRawQuery() != null) {
            throw new IllegalArgumentException("출처에는 경로와 쿼리를 둘 수 없습니다");
        }
        return normalize(uri);
    }

    // Referer나 콜백 주소처럼 경로가 붙은 URL에서 출처만 꺼낸다. 경로·쿼리는 사용하지 않는다.
    public static String originOfUrl(String value) {
        return normalize(parse(value));
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("출처가 비어 있습니다");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getUserInfo() != null || uri.getRawFragment() != null
                || uri.getRawAuthority().endsWith(":")
                || uri.getHost().contains("%")
                || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("http 또는 https 출처가 필요합니다");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("출처 형식이 올바르지 않습니다", e);
        }
    }

    /** IPv6 리터럴만 파싱하므로 DNS 조회 없이 축약·확장 표기를 같은 출처 키로 만든다. */
    private static String normalizeHost(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.startsWith("[")) {
            return host;
        }
        try {
            return "[" + InetAddress.getByName(host).getHostAddress() + "]";
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("IPv6 출처 형식이 올바르지 않습니다", e);
        }
    }

    private static String normalize(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        return scheme + "://" + normalizeHost(uri) + (defaultPort ? "" : ":" + port);
    }
}
