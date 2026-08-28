package com.sssok.application.download;

import java.time.Instant;

public record BatchDownloadFile(Long mediaId, String fileName, String downloadUrl, Instant expiresAt) {
}
