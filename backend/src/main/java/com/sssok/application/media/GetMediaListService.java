package com.sssok.application.media;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaListService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final MediaDetailAssembler assembler;

    @Transactional(readOnly = true)
    public List<MediaDetail> list(Long roomId, Long folderId) {
        return assembler.assemble(find(roomId, folderId));
    }

    private List<StoredFile> find(Long roomId, Long folderId) {
        if (folderId == null) {
            return fileRepository.findAllByRoomIdAndStatusInOrderByNewest(
                roomId, UploadStatus.visibleStatuses());
        }
        requireFolderInRoom(roomId, folderId);
        // 방 조건을 여기서도 건다. 폴더가 이 방 소속인 건 확인했지만, 담긴 미디어까지
        // 같은 방인지는 보장되지 않아서다.
        return fileRepository.findAllByRoomIdAndIdInAndStatusInOrderByNewest(
            roomId, folderMediaRepository.findMediaIdsByFolderId(folderId),
            UploadStatus.visibleStatuses());
    }

    // 다른 방 폴더로 남의 사진을 들여다보지 못하도록 소속까지 확인한다.
    private void requireFolderInRoom(Long roomId, Long folderId) {
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));
    }
}
