package com.sssok.application.mediafolder;

import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.folder.Folder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더에 담기. mediaIds 전부를 folderId 폴더 하나에 담는다.
// 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class AddMediaToFoldersService {

    private final RoomFolders roomFolders;
    private final MediaExistenceResolver mediaExistenceResolver;
    private final FolderMediaRepository folderMediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AddMediaToFoldersResult add(Long roomId, List<Long> mediaIds, Long folderId) {
        requireNotEmpty(mediaIds, folderId);

        Folder folder = roomFolders.requireAllInRoom(roomId, List.of(folderId)).get(0);
        MediaExistence media = mediaExistenceResolver.resolve(mediaIds);

        int updatedCount = folderMediaRepository.attachToFolder(folderId, media.existingIds());
        int alreadyInCount = media.existingIds().size() - updatedCount;
        FolderSummary summary = FolderSummary.of(folder, folderMediaRepository.countByFolderId(folderId));

        if (!media.existingIds().isEmpty()) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.added(roomId, media.existingIds(), List.of(summary)));
        }
        return new AddMediaToFoldersResult(updatedCount, alreadyInCount, media.notFoundIds(), summary);
    }

    private void requireNotEmpty(List<Long> mediaIds, Long folderId) {
        if (mediaIds == null || mediaIds.isEmpty() || folderId == null) {
            throw new InvalidMediaFolderParamException();
        }
    }
}
