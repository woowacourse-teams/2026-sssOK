package com.sssok.application.media;

public record UploadFileCommand(String fileName, String mimeType, Long size) {

    // 걸러내지 않고 예약을 시도하면 저장 단계에서 터져 요청 전체가 500 이 된다.
    public boolean lacksRequiredMetadata() {
        return fileName == null || fileName.isBlank() || size == null;
    }
}
