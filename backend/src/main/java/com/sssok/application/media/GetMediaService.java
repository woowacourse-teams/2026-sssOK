package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaService {

    private final FileRepository fileRepository;
    private final MediaDetailAssembler assembler;

    // 다른 방 미디어와 아직 실물이 없는 미디어를 모두 404 로 낸다. 403 으로 나누면
    // 남의 방에 그 ID 가 있다는 사실이 드러난다.
    @Transactional(readOnly = true)
    public MediaDetail get(Long roomId, Long mediaId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .filter(found -> found.getStatus().isVisible())
            .orElseThrow(MediaNotFoundException::new);

        return assembler.assemble(List.of(file)).getFirst();
    }
}
