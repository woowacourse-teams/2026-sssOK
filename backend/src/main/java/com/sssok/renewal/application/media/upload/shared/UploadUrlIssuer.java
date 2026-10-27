package com.sssok.renewal.application.media.upload.shared;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.config.UploadProperties;
import com.sssok.renewal.outgoing.storage.FileStorage;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UploadUrlIssuer {
    private final FileStorage fileStorage;
    private final UploadProperties uploadProperties;

    public UploadUrl issue(StoredFile file) {
        Duration validityDuration = uploadProperties.presignedUrlTtl();
        String presignedUrl = fileStorage.presignPut(
            file.getStorageKey(), file.getMediaType().contentType(), validityDuration);
        return UploadUrl.of(file, presignedUrl, validityDuration);
    }
}
