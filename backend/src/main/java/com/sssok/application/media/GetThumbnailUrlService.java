package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.ThumbnailNotFoundException;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.DownloadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetThumbnailUrlService {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final DownloadProperties downloadProperties;

    public String getUrl(Long roomId, Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .filter(found -> found.getStatus().isVisible())
            .orElseThrow(MediaNotFoundException::new);

        // 아직 워커가 만들지 않았거나(PROCESSING) 영상이라 만들 수 없는 경우다.
        // 미디어 자체는 있으므로 404 MEDIA_NOT_FOUND 와 구분해서 알려준다.
        if (file.getThumbnailKey() == null) {
            throw new ThumbnailNotFoundException();
        }

        // attachment 가 아니라 inline 이다. 목록 타일에 그리는 용도라 저장 대화상자가 뜨면 안 된다.
        return fileStoragePort.presignGet(file.getThumbnailKey(), "inline",
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }
}
