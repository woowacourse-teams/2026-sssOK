package com.sssok.renewal.application.media.upload.reissue;

import com.sssok.renewal.application.media.upload.shared.UploadRoomValidator;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.config.UploadProperties;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.MediaNotFoundException;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReissueUploadValidator {
    private final UploadFileRepository uploadFileRepository;
    private final UploadRoomValidator uploadRoomValidator;
    private final UploadProperties uploadProperties;

    public StoredFile validate(ReissueUploadUrlCommand command) {
        // 완료 등록과 재발급이 같은 파일의 상태를 덮어쓰지 않도록 재발급 트랜잭션에서 잠근다.
        StoredFile file = uploadFileRepository.findByIdForUpdate(command.mediaId())
            .filter(found -> found.getRoomId().equals(command.roomId()))
            .orElseThrow(MediaNotFoundException::new);
        if (!file.isUploadedBy(command.requesterId())) {
            throw new MediaForbiddenException();
        }
        uploadRoomValidator.validate(command.roomId(), command.requesterId());
        command.validate();
        if (command.newSize() != null) {
            file.changeFileSize(new FileSize(command.newSize()), uploadProperties.sizePolicy());
        }
        return file;
    }
}
