package com.sssok.application.media;

import com.sssok.application.media.exception.InvalidMediaDeleteParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.TooManyMediaException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.domain.file.FilePermissionPolicy;
import com.sssok.domain.file.StoredFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteMediaService {

    private static final int MAX_MEDIA_COUNT = 500;

    private final FileRepository fileRepository;
    private final RoomPermissionPort roomPermissionPort;
    private final MediaDeleter mediaDeleter;

    public Long deleteOne(Long roomId, Long mediaId, Long requesterId) {
        StoredFile file = fileRepository.findById(mediaId)
            .filter(found -> found.getRoomId().equals(roomId))
            .orElseThrow(MediaNotFoundException::new);
        requireDeletePermission(roomId, requesterId, List.of(file));
        mediaDeleter.delete(roomId, List.of(file));
        return file.getId();
    }

    public DeleteMediaResult deleteAll(Long roomId, List<Long> mediaIds, Long requesterId) {
        requireValid(mediaIds);
        List<Long> distinctIds = mediaIds.stream().distinct().toList();
        Map<Long, StoredFile> filesById = new LinkedHashMap<>();
        fileRepository.findAllByIdIn(distinctIds).stream()
            .filter(file -> file.getRoomId().equals(roomId))
            .forEach(file -> filesById.put(file.getId(), file));

        List<Long> notFoundIds = distinctIds.stream()
            .filter(id -> !filesById.containsKey(id))
            .toList();
        List<StoredFile> files = distinctIds.stream()
            .filter(filesById::containsKey)
            .map(filesById::get)
            .toList();

        requireDeletePermission(roomId, requesterId, files);
        if (!files.isEmpty()) {
            mediaDeleter.delete(roomId, files);
        }
        List<Long> deletedIds = files.stream().map(StoredFile::getId).toList();
        return new DeleteMediaResult(deletedIds.size(), deletedIds, notFoundIds);
    }

    private void requireValid(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty() || mediaIds.stream().anyMatch(id -> id == null)) {
            throw new InvalidMediaDeleteParamException();
        }
        if (mediaIds.size() > MAX_MEDIA_COUNT) {
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
