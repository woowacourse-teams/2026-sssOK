package com.sssok.application.medialike;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 추가·취소 공통: 좋아요 대상이 이 방에서 목록에 보이는 미디어인지 확인한다.
// 없는 미디어·다른 방 미디어·실물이 없는 RESERVED/FAILED 를 모두 404 로 낸다 — GetMediaService 와 같은 이유로,
// 403 으로 나누면 남의 방에 그 ID 가 있다는 사실이 드러난다.
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
