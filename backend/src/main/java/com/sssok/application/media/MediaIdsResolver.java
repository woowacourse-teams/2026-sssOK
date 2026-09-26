package com.sssok.application.media;

import com.sssok.application.media.exception.InvalidMediaIdsException;
import com.sssok.application.media.exception.TooManyMediaException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaIdsResolver {

    private final FileRepository fileRepository;

    public ResolvedMediaIds resolve(Long roomId, List<Long> mediaIds) {
        return resolveDistinctIds(roomId, distinctIds(mediaIds));
    }

    public ResolvedMediaIds resolve(Long roomId, List<Long> mediaIds, int maxCount) {
        List<Long> distinctIds = distinctIds(mediaIds);
        if (distinctIds.size() > maxCount) {
            throw new TooManyMediaException(maxCount);
        }
        return resolveDistinctIds(roomId, distinctIds);
    }

    private ResolvedMediaIds resolveDistinctIds(Long roomId, List<Long> distinctIds) {
        Map<Long, StoredFile> filesById = new LinkedHashMap<>();
        fileRepository.findAllByRoomIdAndIdIn(roomId, distinctIds)
            .forEach(file -> filesById.put(file.getId(), file));
        List<StoredFile> files = distinctIds.stream()
            .filter(filesById::containsKey)
            .map(filesById::get)
            .toList();
        List<Long> notFoundIds = distinctIds.stream()
            .filter(id -> !filesById.containsKey(id))
            .toList();
        return new ResolvedMediaIds(files, notFoundIds);
    }

    private List<Long> distinctIds(List<Long> mediaIds) {
        validate(mediaIds);
        return mediaIds.stream().distinct().toList();
    }

    private void validate(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidMediaIdsException();
        }
    }
}
