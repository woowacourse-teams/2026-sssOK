package com.sssok.renewal.application.download.batch;

import java.time.Instant;

public record BatchDownloadFile(Long mediaId, String fileName, String downloadUrl, Instant expiresAt) {
}
