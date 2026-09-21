package com.sssok.application.media;

import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.TooManyMediaException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.domain.file.FilePermissionPolicy;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteMediaService {

    private static final int MAX_MEDIA_COUNT = 500;

    private final FileRepository fileRepository;
    private final RoomPermissionPort roomPermissionPort;
    private final MediaDeleter mediaDeleter;
    private final MediaSelectionResolver mediaSelectionResolver;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;

    public Long deleteOne(Long roomId, Long mediaId, Long requesterId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .orElseThrow(MediaNotFoundException::new);
        requireDeletePermission(roomId, requesterId, List.of(file));
        mediaDeleter.delete(roomId, List.of(file));
        return file.getId();
    }

    public DeleteMediaResult deleteAll(Long roomId, MediaSelection selection, Long folderId,
                                       Long requesterId, MediaUploaderFilter uploader) {
        ResolvedMediaSelection resolved =
            mediaSelectionResolver.resolve(roomId, selection, requesterId, uploader);
        List<StoredFile> files = limitToFolder(roomId, folderId, resolved.files());
        requireWithinLimit(files);

        requireDeletePermission(roomId, requesterId, files);
        if (!files.isEmpty()) {
            mediaDeleter.delete(roomId, files);
        }
        List<Long> deletedIds = files.stream().map(StoredFile::getId).toList();
        return new DeleteMediaResult(deletedIds.size(), deletedIds, resolved.notFoundIds());
    }

    private List<StoredFile> limitToFolder(Long roomId, Long folderId, List<StoredFile> files) {
        if (folderId == null) {
            return files;
        }
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));
        List<Long> inFolder = folderMediaRepository.findMediaIdsByFolderId(folderId);
        return files.stream().filter(file -> inFolder.contains(file.getId())).toList();
    }

    private void requireWithinLimit(List<StoredFile> files) {
        if (files.size() > MAX_MEDIA_COUNT) {
            throw new TooManyMediaException(MAX_MEDIA_COUNT);
        }
    }

    private void requireDeletePermission(Long roomId, Long requesterId, List<StoredFile> files) {
        boolean isHost = roomPermissionPort.isHost(roomId, requesterId);
        boolean canDeleteAll = files.stream()
            .allMatch(file -> FilePermissionPolicy.canDelete(file, requesterId, isHost));
        if (!canDeleteAll) {
            throw new MediaForbiddenException();
        }
    }
}
