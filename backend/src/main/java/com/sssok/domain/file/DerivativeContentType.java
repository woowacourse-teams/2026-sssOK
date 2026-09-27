package com.sssok.domain.file;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// 파생본 키에 실어 보낼 Content-Type 을 키 자체에서 읽어낸다.
//
// 저장된 파생본이 어떤 포맷인지는 행에 따로 적지 않는다. 대신 키의 확장자가 그것을 말한다
// (StorageKey.thumbnail/preview 가 파생본 포맷의 확장자를 붙인다). 이렇게 두면 설정을 바꿔
// 포맷을 갈아끼워도, 그 전에 만들어진 파생본이 여전히 제 Content-Type 으로 서명된다 —
// 지금 설정값을 보고 정하면 예전 JPEG 썸네일이 image/webp 로 서명돼 전부 깨진다.
public final class DerivativeContentType {

    // 확장자를 읽지 못하는 키. 있을 수 없지만, 여기서 예외를 던지면 목록 조회 전체가 500 이 된다.
    // 그렇다고 image/jpeg 로 찍으면 실제 포맷과 어긋난 채 브라우저가 그리려다 깨지므로,
    // 어떤 이미지인지 단정하지 않는 값으로 내려보낸다.
    private static final String UNKNOWN = "application/octet-stream";

    // 파생본으로 내보내는 포맷의 확장자·Content-Type 은 DerivativeFormat 이 갖는다. 여기서
    // 다시 적으면 AVIF 같은 포맷을 추가할 때 한쪽만 고치게 되므로 enum 에서 그대로 끌어온다.
    private static final Map<String, String> BY_EXTENSION = byExtension();

    private DerivativeContentType() {
    }

    public static String of(StorageKey key) {
        String value = key.value();
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0) {
            return UNKNOWN;
        }
        String extension = value.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.getOrDefault(extension, UNKNOWN);
    }

    private static Map<String, String> byExtension() {
        Map<String, String> byExtension = new HashMap<>();
        for (DerivativeFormat format : DerivativeFormat.values()) {
            byExtension.put(format.extension(), format.contentType());
        }
        // 포맷을 통일하기 전에 만들어 둔 파생본들. 그때는 원본과 같은 포맷으로 줄였기 때문에
        // 파생본 포맷이 아닌 확장자도 키에 남아 있다.
        byExtension.put("jpeg", "image/jpeg");
        byExtension.put("png", "image/png");
        byExtension.put("gif", "image/gif");
        return Map.copyOf(byExtension);
    }
}
