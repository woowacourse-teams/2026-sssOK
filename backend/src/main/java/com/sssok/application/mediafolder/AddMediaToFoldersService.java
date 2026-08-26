package com.sssok.application.mediafolder;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 폴더에 담기. mediaIds x folderIds 모든 조합을 연결한다(카테시안 곱).
// 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class AddMediaToFoldersService {

    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final FileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AddMediaToFoldersResult add(Long roomId, List<Long> mediaIds, List<Long> folderIds) {
        requireNotEmpty(mediaIds, folderIds);

        List<Long> distinctFolderIds = folderIds.stream().distinct().toList();
        List<Long> distinctMediaIds = mediaIds.stream().distinct().toList();

        List<Folder> folders = requireFoldersInRoom(roomId, distinctFolderIds);

        List<Long> existingMediaIds = fileRepository.findExistingIds(distinctMediaIds);
        List<Long> notFoundMediaIds = distinctMediaIds.stream()
            .filter(id -> !existingMediaIds.contains(id))
            .toList();

        int updatedCount = folderMediaRepository.attachAll(distinctFolderIds, existingMediaIds);
        int totalPairs = distinctFolderIds.size() * existingMediaIds.size();
        int alreadyInCount = totalPairs - updatedCount;

        List<FolderSummary> summaries = summarize(folders);

        if (!existingMediaIds.isEmpty()) {
            eventPublisher.publishEvent(MediaFoldersUpdatedEvent.added(roomId, existingMediaIds, summaries));
        }
        return new AddMediaToFoldersResult(updatedCount, alreadyInCount, notFoundMediaIds, summaries);
    }

    private void requireNotEmpty(List<Long> mediaIds, List<Long> folderIds) {
        if (mediaIds == null || mediaIds.isEmpty() || folderIds == null || folderIds.isEmpty()) {
            throw new InvalidMediaFolderParamException();
        }
    }

    private List<Folder> requireFoldersInRoom(Long roomId, List<Long> folderIds) {
        List<Folder> found = folderRepository.findAllById(folderIds).stream()
            .filter(folder -> folder.belongsTo(roomId))
            .toList();
        Set<Long> foundIds = found.stream().map(Folder::getId).collect(Collectors.toSet());
        List<Long> missing = folderIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new FolderNotFoundException(missing);
        }
        return found;
    }

    private List<FolderSummary> summarize(List<Folder> folders) {
        return folders.stream()
            .map(folder -> FolderSummary.of(folder, folderMediaRepository.countByFolderId(folder.getId())))
            .toList();
    }
}
