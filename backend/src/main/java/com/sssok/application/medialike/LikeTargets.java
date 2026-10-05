package com.sssok.application.medialike;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LikeTargets {

    private final FileRepository fileRepository;

    StoredFile requireVisibleInRoom(Long roomId, Long mediaId) {
        return fileRepository.findById(mediaId)
            .filter(file -> file.getRoomId().equals(roomId))
            .filter(file -> file.getStatus().isVisible())
            .orElseThrow(MediaNotFoundException::new);
    }
}
