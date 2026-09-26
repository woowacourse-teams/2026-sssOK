package com.sssok.application.mediafolder;

import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.media.MediaIdsResolver;
import com.sssok.application.media.ResolvedMediaIds;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.UploadStatus;
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
    private final MediaIdsResolver mediaIdsResolver;
    private final FolderMediaRepository folderMediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AddMediaToFoldersResult add(Long roomId, List<Long> requestedMediaIds, Long folderId) {
        requireFolder(folderId);

        Folder folder = roomFolders.requireAllInRoom(roomId, List.of(folderId)).get(0);
        ResolvedMediaIds media = mediaIdsResolver.resolveVisible(roomId, requestedMediaIds);
        List<Long> mediaIds = media.files().stream().map(file -> file.getId()).toList();

        int updatedCount = folderMediaRepository.attachToFolder(folderId, mediaIds);
        int alreadyInCount = mediaIds.size() - updatedCount;
        FolderSummary summary = FolderSummary.of(folder,
            folderMediaRepository.countByFolderIdAndStatusIn(folderId, UploadStatus.visibleStatuses()));

        if (updatedCount > 0) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.added(roomId, mediaIds, List.of(summary)));
        }
        return new AddMediaToFoldersResult(updatedCount, alreadyInCount, media.notFoundIds(), summary);
    }

    private void requireFolder(Long folderId) {
        if (folderId == null) {
            throw new InvalidMediaFolderParamException();
        }
    }
}
