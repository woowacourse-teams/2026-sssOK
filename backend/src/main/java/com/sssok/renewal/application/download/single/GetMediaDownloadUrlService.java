package com.sssok.renewal.application.download.single;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.download.exception.DownloadMediaNotFoundException;
import com.sssok.renewal.application.download.shared.DownloadUrlIssuer;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import com.sssok.renewal.outgoing.database.DownloadFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaDownloadUrlService {
    private final DownloadFileRepository downloadFileRepository;
    private final DownloadUrlIssuer downloadUrlIssuer;
    private final DownloadProperties downloadProperties;

    public String getUrl(Long roomId, Long mediaId) {
        StoredFile file = downloadFileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            // RESERVED·FAILED 는 스토리지에 실물이 없어 없는 미디어와 같게 본다.
            .filter(found -> found.getStatus().isDownloadable())
            .orElseThrow(DownloadMediaNotFoundException::new);
        return downloadUrlIssuer.issue(file.getStorageKey(), file.getOriginalFileName(),
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }
}
