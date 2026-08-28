package com.sssok.application.download;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.DownloadFileNames;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 압축 없이 선택한 파일마다 서명 GET URL을 즉시 발급한다. 대상 선정 규칙(mediaIds/folderId,
// 최대 개수, READY만 대상)은 zip 압축(CreateDownloadJobService)과 같아 DownloadTargetResolver를
// 그대로 재사용한다. 잡을 만들지 않는 동기 흐름이라 별도 상태 조회가 필요 없다.
@Service
@RequiredArgsConstructor
public class CreateBatchDownloadService {

    private final DownloadTargetResolver downloadTargetResolver;
    private final FileStoragePort fileStoragePort;
    private final DownloadProperties downloadProperties;

    public List<BatchDownloadFile> create(Long roomId, List<Long> mediaIds, Long folderId) {
        List<StoredFile> targets = downloadTargetResolver.resolve(roomId, mediaIds, folderId);
        List<String> fileNames =
            DownloadFileNames.deduplicate(targets.stream().map(StoredFile::getOriginalFileName).toList());
        Instant expiresAt = Instant.now().plus(downloadProperties.presignedGetTtl());

        return IntStream.range(0, targets.size())
            .mapToObj(i -> toBatchFile(targets.get(i), fileNames.get(i), expiresAt))
            .toList();
    }

    private BatchDownloadFile toBatchFile(StoredFile file, String fileName, Instant expiresAt) {
        String contentDisposition = DownloadFileNames.contentDispositionOf(fileName);
        String url = fileStoragePort.presignGet(file.getStorageKey(), contentDisposition,
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
        return new BatchDownloadFile(file.getId(), fileName, url, expiresAt);
    }
}
