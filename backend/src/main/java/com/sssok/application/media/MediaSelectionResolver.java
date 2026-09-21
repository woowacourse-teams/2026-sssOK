package com.sssok.application.media;

import com.sssok.application.media.exception.InvalidMediaSelectionException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaSelectionResolver {

    private final FileRepository fileRepository;

    public ResolvedMediaSelection resolve(Long roomId, MediaSelection selection) {
        validate(selection);
        List<Long> distinctIds = selection.ids().stream().distinct().toList();
        if (selection.mode() == MediaSelectionMode.EXCLUDE) {
            return new ResolvedMediaSelection(
                fileRepository.findAllByRoomIdAndIdNotIn(roomId, distinctIds), List.of());
        }

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
        return new ResolvedMediaSelection(files, notFoundIds);
    }

    private void validate(MediaSelection selection) {
        if (selection == null || selection.mode() == null || selection.ids() == null
            || selection.ids().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidMediaSelectionException();
        }
    }
}
