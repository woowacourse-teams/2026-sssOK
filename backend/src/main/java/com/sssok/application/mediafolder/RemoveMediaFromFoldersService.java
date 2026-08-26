package com.sssok.application.mediafolder;

import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import java.util.List;
import java.util.Map;
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
    private final MediaExistenceResolver mediaExistenceResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RemoveMediaFromFoldersResult remove(Long roomId, List<Long> mediaIds, List<Long> folderIds) {
        requireValid(mediaIds);

        MediaExistence media = mediaExistenceResolver.resolve(mediaIds);
        List<Long> hadFolderBefore = mediaIdsWithAnyFolder(media.existingIds());

        Detachment detachment = isUnscoped(folderIds)
            ? detachFromEveryFolder(media.existingIds())
            : detachFromChosenFolders(roomId, folderIds, media.existingIds());

        List<Long> movedToRootMediaIds = movedToRoot(hadFolderBefore, media.existingIds());
        List<FolderSummary> summaries = summarize(detachment.targetFolders());

        if (!media.existingIds().isEmpty()) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.removed(roomId, media.existingIds(), summaries));
        }
        return new RemoveMediaFromFoldersResult(
            detachment.updatedCount(), movedToRootMediaIds, media.notFoundIds(), summaries);
    }

    private boolean isUnscoped(List<Long> folderIds) {
        return folderIds == null || folderIds.isEmpty();
    }

    // 폴더 지정 없이 꺼내기: 지금 이 미디어들이 속한 폴더를 전부 찾아 관계를 끊는다.
    private Detachment detachFromEveryFolder(List<Long> existingMediaIds) {
        if (existingMediaIds.isEmpty()) {
            return new Detachment(0, List.of());
        }
        List<Folder> targetFolders =
            folderRepository.findAllById(folderMediaRepository.findFolderIdsContainingMedia(existingMediaIds));
        int updatedCount = (int) folderMediaRepository.detachFromAllFolders(existingMediaIds);
        return new Detachment(updatedCount, targetFolders);
    }

    // 폴더를 지정한 꺼내기: 그 폴더들이 이 방 소속인지 먼저 확인하고, 지정된 폴더와의 관계만 끊는다.
    private Detachment detachFromChosenFolders(Long roomId, List<Long> folderIds, List<Long> existingMediaIds) {
        List<Long> distinctFolderIds = folderIds.stream().distinct().toList();
        List<Folder> targetFolders = roomFolders.requireAllInRoom(roomId, distinctFolderIds);
        int updatedCount = folderMediaRepository.detachFromFolders(distinctFolderIds, existingMediaIds);
        return new Detachment(updatedCount, targetFolders);
    }

    private record Detachment(int updatedCount, List<Folder> targetFolders) {
    }

    // 꺼내기 전에는 폴더가 있었는데 꺼내고 나니 없어진 미디어만 "루트로 이동"한 것이다.
    // 원래부터 루트였던 미디어는 이번 요청으로 바뀐 게 없으므로 포함하지 않는다.
    private List<Long> movedToRoot(List<Long> hadFolderBefore, List<Long> existingMediaIds) {
        List<Long> stillHasFolder = mediaIdsWithAnyFolder(existingMediaIds);
        return hadFolderBefore.stream().filter(id -> !stillHasFolder.contains(id)).toList();
    }

    private List<FolderSummary> summarize(List<Folder> folders) {
        if (folders.isEmpty()) {
            return List.of();
        }
        List<Long> folderIds = folders.stream().map(Folder::getId).toList();
        Map<Long, Long> photoCounts = folderMediaRepository.countByFolderIds(folderIds);
        return folders.stream()
            .map(folder -> FolderSummary.of(folder, photoCounts.getOrDefault(folder.getId(), 0L)))
            .toList();
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
