package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.MediaNotReadyException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.DownloadFileNames;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DownloadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaDownloadUrlService {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final DownloadProperties downloadProperties;

    public String getUrl(Long roomId, Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .orElseThrow(MediaNotFoundException::new);

        if (file.getStatus() == UploadStatus.PROCESSING) {
            throw new MediaNotReadyException();
        }
        // RESERVED/FAILED는 실물이 스토리지에 없는 상태라, 존재하지 않는 것과 동일하게 취급한다.
        if (file.getStatus() != UploadStatus.READY) {
            throw new MediaNotFoundException();
        }

        String contentDisposition = DownloadFileNames.contentDispositionOf(file.getOriginalFileName());
        return fileStoragePort.presignGet(file.getStorageKey(), contentDisposition,
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }
}
