package com.sssok.domain.file;

import java.util.Locale;

// 파생본 키에 실어 보낼 Content-Type 을 키 자체에서 읽어낸다.
//
// 저장된 파생본이 어떤 포맷인지는 행에 따로 적지 않는다. 대신 키의 확장자가 그것을 말한다
// (StorageKey.thumbnail/preview 가 파생본 포맷의 확장자를 붙인다). 이렇게 두면 설정을 바꿔
// 포맷을 갈아끼워도, 그 전에 만들어진 파생본이 여전히 제 Content-Type 으로 서명된다 —
// 지금 설정값을 보고 정하면 예전 JPEG 썸네일이 image/webp 로 서명돼 전부 깨진다.
public final class DerivativeContentType {

    // 확장자를 읽지 못하는 키. 있을 수 없지만, 여기서 예외를 던지면 목록 조회 전체가 500 이 된다.
    private static final String FALLBACK = "image/jpeg";

    private DerivativeContentType() {
    }

    public static String of(StorageKey key) {
        String value = key.value();
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0) {
            return FALLBACK;
        }
        return switch (value.substring(lastDot + 1).toLowerCase(Locale.ROOT)) {
            case "webp" -> "image/webp";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> FALLBACK;
        };
    }
}
