package com.sssok.domain.file;

// 사진 파생본을 내보낼 포맷. 측정 결과 WebP 로 정했고, 비교표는
// docs/backend/IMAGE_DERIVATIVE_FORMAT.md 에 있다. JPEG 을 남겨 둔 것은 되돌릴 자리다 —
// WebP 인코더의 네이티브 라이브러리가 어떤 플랫폼에서 안 뜨면 설정만 바꿔 내려앉을 수 있어야 한다.
public enum DerivativeFormat {

    WEBP("webp", "image/webp"),
    JPEG("jpg", "image/jpeg");

    private final String extension;
    private final String contentType;

    DerivativeFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    // 서명 URL 에 실어 보낼 값. 파생본 키의 확장자가 아니라 이 값으로 브라우저가 그릴 방법을 정한다.
    public String contentType() {
        return contentType;
    }
}
