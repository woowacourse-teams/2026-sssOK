package com.sssok.renewal.application.download.shared;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.domain.download.DownloadFileNames;
import com.sssok.renewal.outgoing.storage.FileStorage;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 브라우저가 받은 파일을 이 이름으로 저장하도록 Content-Disposition 과 Content-Type 을 서명에 싣는다.
// 단건·다건·zip 이 같은 규칙으로 파일명을 내려준다.
@Component
@RequiredArgsConstructor
public class DownloadUrlIssuer {
    private final FileStorage fileStorage;

    public String issue(StorageKey storageKey, String downloadFileName, String contentType, Duration ttl) {
        return fileStorage.presignGet(storageKey, DownloadFileNames.contentDispositionOf(downloadFileName),
            contentType, ttl);
    }
}
