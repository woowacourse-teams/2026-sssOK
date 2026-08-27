package com.sssok.application.media;

public record UploadFileCommand(String fileName, String mimeType, Long size) {
}
