package com.sssok.renewal.application.download.batch;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.download.shared.DownloadTargetCommand;
import com.sssok.renewal.application.download.shared.DownloadTargetResolver;
import com.sssok.renewal.application.download.shared.DownloadUrlIssuer;
import com.sssok.renewal.domain.download.DownloadFileNames;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 압축 없이 대상 파일마다 서명 GET URL 을 바로 발급한다. 잡을 만들지 않아 상태 조회가 없다.
// 대상 규칙은 zip 과 같아 DownloadTargetResolver 를 함께 쓴다.
@Service
@RequiredArgsConstructor
public class CreateBatchDownloadService {
    private final DownloadTargetResolver downloadTargetResolver;
    private final DownloadUrlIssuer downloadUrlIssuer;
    private final DownloadProperties downloadProperties;

    public List<BatchDownloadFile> create(DownloadTargetCommand command) {
        List<StoredFile> targets = downloadTargetResolver.resolve(command);
        // 같은 원본 파일명은 번호를 붙여, 한 폴더에 저장해도 서로 덮어쓰지 않게 한다.
        List<String> fileNames =
            DownloadFileNames.deduplicate(targets.stream().map(StoredFile::getOriginalFileName).toList());
        Instant expiresAt = Instant.now().plus(downloadProperties.presignedGetTtl());
        return IntStream.range(0, targets.size())
            .mapToObj(i -> toBatchFile(targets.get(i), fileNames.get(i), expiresAt))
            .toList();
    }

    private BatchDownloadFile toBatchFile(StoredFile file, String fileName, Instant expiresAt) {
        String url = downloadUrlIssuer.issue(file.getStorageKey(), fileName,
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
        return new BatchDownloadFile(file.getId(), fileName, url, expiresAt);
    }
}
