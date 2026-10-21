package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.renewal.application.download.shared.DownloadUrlIssuer;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.config.DownloadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaDownloadUrlService {

    private final FileRepository fileRepository;
    private final DownloadUrlIssuer downloadUrlIssuer;
    private final DownloadProperties downloadProperties;

    public String getUrl(Long roomId, Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .orElseThrow(MediaNotFoundException::new);

        // RESERVED/FAILED는 실물이 스토리지에 없는 상태라, 존재하지 않는 것과 동일하게 취급한다.
        if (!file.getStatus().isDownloadable()) {
            throw new MediaNotFoundException();
        }

        return downloadUrlIssuer.issue(file.getStorageKey(), file.getOriginalFileName(),
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }
}
