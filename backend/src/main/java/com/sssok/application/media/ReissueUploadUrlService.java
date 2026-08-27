package com.sssok.application.media;

import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.exception.InvalidFileSizeException;
import com.sssok.infrastructure.config.UploadProperties;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 최초 발급 이후 방 권한이 바뀌었을 수 있어 여기서 다시 검사한다.
@Service
@RequiredArgsConstructor
public class ReissueUploadUrlService {

    private static final String PUT = "PUT";

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final RoomPermissionPort roomPermissionPort;
    private final UploadProperties uploadProperties;

    @Transactional
    public ReissuedUploadUrl reissue(Long roomId, Long mediaId, Long requesterId, Long newSize) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .orElseThrow(MediaNotFoundException::new);

        // 방장이라도 남의 예약은 재발급하지 못한다.
        if (!file.isUploadedBy(requesterId)) {
            throw new MediaForbiddenException();
        }
        if (!roomPermissionPort.canUpload(roomId, requesterId)) {
            throw new UploadNotAllowedException();
        }
        if (newSize != null) {
            changeSize(file, newSize);
        }

        file.reissueUploadUrl(uploadProperties.maxRetryCount(), Instant.now());
        StoredFile saved = fileRepository.save(file);

        String contentType = saved.getMediaType().contentType();
        String url = fileStoragePort.presignPut(
            saved.getStorageKey(), contentType, uploadProperties.presignedUrlTtl());

        return new ReissuedUploadUrl(saved.getId(), saved.getOriginalFileName(), url, PUT,
            Map.of("Content-Type", contentType),
            (int) uploadProperties.presignedUrlTtl().toSeconds(),
            saved.getRetryCount(), uploadProperties.maxRetryCount());
    }

    // 재압축해서 다시 올리는 경우. 0 이하는 400, 한도 초과는 413 으로 갈린다.
    private void changeSize(StoredFile file, Long newSize) {
        try {
            file.changeFileSize(new FileSize(newSize));
        } catch (InvalidFileSizeException e) {
            throw new InvalidUploadParamException("파일 크기가 올바르지 않습니다");
        }
    }
}
