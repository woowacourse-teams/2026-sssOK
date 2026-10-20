package com.sssok.renewal.application.media.upload.shared;

import com.sssok.renewal.application.media.upload.exception.UploadNotAllowedException;
import com.sssok.renewal.outgoing.database.UploadRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UploadRoomValidator {
    private final UploadRoomRepository uploadRoomRepository;

    public void validate(Long roomId, Long uploaderId) {
        boolean uploadAllowed = uploadRoomRepository.findById(roomId)
            .map(room -> room.canUpload(uploaderId))
            .orElse(false);
        if (!uploadAllowed) {
            throw new UploadNotAllowedException();
        }
    }
}
