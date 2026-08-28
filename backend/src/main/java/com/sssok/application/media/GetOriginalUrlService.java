package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.MediaNotReadyException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DownloadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 원본을 화면에 띄우기 위한 경로. 저장용 다운로드(#84)와 나뉘는 지점은 Content-Disposition 하나다 —
// attachment 로 내려주면 브라우저가 저장 대화상자를 띄워, 크게 보기에는 쓸 수 없다.
//
// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetOriginalUrlService {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final DownloadProperties downloadProperties;

    public String getUrl(Long roomId, Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .filter(found -> found.getStatus().isVisible())
            .orElseThrow(MediaNotFoundException::new);

        // 워커가 아직 손대는 중이면 원본이 바뀌는 중일 수 있다. 다운로드와 같은 기준으로 막는다.
        if (file.getStatus() != UploadStatus.READY) {
            throw new MediaNotReadyException();
        }

        return fileStoragePort.presignGet(file.getStorageKey(), "inline",
            file.getMediaType().contentType(), downloadProperties.presignedGetTtl());
    }
}
