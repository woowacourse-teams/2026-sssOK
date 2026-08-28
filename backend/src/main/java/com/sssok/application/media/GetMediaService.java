package com.sssok.application.media;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.file.FilePermissionPolicy;
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
    private final RoomRepository roomRepository;
    private final MediaDetailAssembler assembler;

    // 다른 방 미디어와 아직 실물이 없는 미디어를 모두 404 로 낸다. 403 으로 나누면
    // 남의 방에 그 ID 가 있다는 사실이 드러난다.
    @Transactional(readOnly = true)
    public MediaFullDetail get(Long roomId, Long mediaId, Long requesterId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .filter(found -> found.getStatus().isVisible())
            .orElseThrow(MediaNotFoundException::new);

        MediaDetail media = assembler.assemble(List.of(file)).getFirst();
        return MediaFullDetail.of(file, media, canDelete(file, roomId, requesterId));
    }

    // 올린 본인과 방장이 지울 수 있다. 프론트가 삭제 버튼을 보일지 정하는 데 쓴다.
    private boolean canDelete(StoredFile file, Long roomId, Long requesterId) {
        boolean isHost = roomRepository.findById(roomId)
            .map(room -> room.getHostId().equals(requesterId))
            .orElse(false);
        return FilePermissionPolicy.canDelete(file, requesterId, isHost);
    }
}
