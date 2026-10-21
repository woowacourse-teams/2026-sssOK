package com.sssok.application.download;

import com.sssok.renewal.application.download.shared.DownloadTargetCommand;
import com.sssok.renewal.application.download.shared.DownloadTargetResolver;
import com.sssok.renewal.application.download.shared.DownloadUrlIssuer;
import com.sssok.renewal.domain.download.DownloadFileNames;
import com.sssok.domain.file.StoredFile;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 압축 없이 선택한 파일마다 서명 GET URL을 즉시 발급한다. 대상 선정 규칙(mediaIds/folderId,
// 최대 개수, 다운로드 가능한 상태만)은 zip 압축(CreateDownloadJobService)과 같아
// DownloadTargetResolver를 그대로 재사용한다. 잡을 만들지 않는 동기 흐름이라 별도 상태 조회가 필요 없다.
@Service
@RequiredArgsConstructor
public class CreateBatchDownloadService {

    private final DownloadTargetResolver downloadTargetResolver;
    private final DownloadUrlIssuer downloadUrlIssuer;
    private final DownloadProperties downloadProperties;

    public List<BatchDownloadFile> create(Long roomId, Long requesterId, List<Long> mediaIds,
                                          Long folderId, MediaUploaderFilter uploader) {
        List<StoredFile> targets = downloadTargetResolver.resolve(
            new DownloadTargetCommand(roomId, requesterId, mediaIds, folderId, uploader));
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
