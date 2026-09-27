package com.sssok.application.mediafolder;

import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.media.MediaIdsResolver;
import com.sssok.application.media.ResolvedMediaIds;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import java.util.List;
import java.util.Map;
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

        int alreadyInCount = countAlreadyIn(folderId, mediaIds);
        int updatedCount = folderMediaRepository.attachToFolderIfStatusIn(
            folderId, mediaIds, UploadStatus.visibleStatuses());
        FolderSummary summary = FolderSummary.of(folder,
            folderMediaRepository.countByFolderIdAndStatusIn(folderId, UploadStatus.visibleStatuses()));

        if (updatedCount > 0) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.added(roomId, mediaIds, List.of(summary)));
        }
        return new AddMediaToFoldersResult(updatedCount, alreadyInCount, media.notFoundIds(), summary);
    }

    // 담기 전에 이미 이 폴더에 있던 개수를 먼저 센다. "대상 수 - 새로 담긴 수"로 빼면,
    // 고른 뒤 FAILED 로 확정돼 담기지 않은 미디어까지 "이미 담겨 있었다"로 잘못 잡힌다.
    private int countAlreadyIn(Long folderId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return 0;
        }
        Map<Long, List<Long>> foldersByMedia = folderMediaRepository.findFolderIdsByMedia(mediaIds);
        return (int) mediaIds.stream()
            .filter(mediaId -> foldersByMedia.getOrDefault(mediaId, List.of()).contains(folderId))
            .count();
    }

    private void requireFolder(Long folderId) {
        if (folderId == null) {
            throw new InvalidMediaFolderParamException();
        }
    }
}
