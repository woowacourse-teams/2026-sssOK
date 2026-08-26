package com.sssok.application.mediafolder;

import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더에서 꺼내기. folderIds를 지정하면 그 폴더들과의 관계만 끊고, 생략하면 속한 모든 폴더에서
// 꺼내 루트로 보낸다. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class RemoveMediaFromFoldersService {

    private final RoomFolders roomFolders;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final FileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RemoveMediaFromFoldersResult remove(Long roomId, List<Long> mediaIds, List<Long> folderIds) {
        requireValid(mediaIds);

        List<Long> distinctMediaIds = mediaIds.stream().distinct().toList();
        List<Long> existingMediaIds = fileRepository.findExistingIds(distinctMediaIds);
        List<Long> notFoundMediaIds = distinctMediaIds.stream()
            .filter(id -> !existingMediaIds.contains(id))
            .toList();

        List<Long> hadFolderBefore = mediaIdsWithAnyFolder(existingMediaIds);

        List<Folder> targetFolders;
        int updatedCount;
        if (folderIds == null || folderIds.isEmpty()) {
            List<Long> affectedFolderIds = existingMediaIds.isEmpty()
                ? List.of()
                : folderMediaRepository.findFolderIdsContainingMedia(existingMediaIds);
            updatedCount = existingMediaIds.isEmpty()
                ? 0
                : (int) folderMediaRepository.detachFromAllFolders(existingMediaIds);
            targetFolders = folderRepository.findAllById(affectedFolderIds);
        } else {
            List<Long> distinctFolderIds = folderIds.stream().distinct().toList();
            targetFolders = roomFolders.requireAllInRoom(roomId, distinctFolderIds);
            updatedCount = folderMediaRepository.detachFromFolders(distinctFolderIds, existingMediaIds);
        }

        List<Long> stillHasFolder = mediaIdsWithAnyFolder(existingMediaIds);
        List<Long> movedToRootMediaIds = hadFolderBefore.stream()
            .filter(id -> !stillHasFolder.contains(id))
            .toList();

        List<FolderSummary> summaries = targetFolders.stream()
            .map(folder -> FolderSummary.of(folder, folderMediaRepository.countByFolderId(folder.getId())))
            .toList();

        if (!existingMediaIds.isEmpty()) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.removed(roomId, existingMediaIds, summaries));
        }
        return new RemoveMediaFromFoldersResult(updatedCount, movedToRootMediaIds, notFoundMediaIds, summaries);
    }

    private void requireValid(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new InvalidMediaFolderParamException();
        }
    }

    // folder_media를 빈 목록으로 조회하면 안 되므로(IN 절에 빈 컬렉션 불가) 미리 걸러준다.
    private List<Long> mediaIdsWithAnyFolder(List<Long> mediaIds) {
        return mediaIds.isEmpty() ? List.of() : folderMediaRepository.findMediaIdsBelongingToAnyFolder(mediaIds);
    }
}
