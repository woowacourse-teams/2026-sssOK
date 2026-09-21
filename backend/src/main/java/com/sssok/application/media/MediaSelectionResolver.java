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

    public ResolvedMediaSelection resolve(Long roomId, MediaSelection selection,
                                          Long requesterId, MediaUploaderFilter uploader) {
        validate(selection);
        List<Long> distinctIds = selection.ids().stream().distinct().toList();
        if (selection.mode() == MediaSelectionMode.EXCLUDE) {
            return new ResolvedMediaSelection(
                filterByUploader(fileRepository.findAllByRoomIdAndIdNotIn(roomId, distinctIds),
                    requesterId, uploader), List.of());
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
        return new ResolvedMediaSelection(
            filterByUploader(files, requesterId, uploader), notFoundIds);
    }

    public List<StoredFile> filterByUploader(List<StoredFile> files, Long requesterId,
                                             MediaUploaderFilter uploader) {
        MediaUploaderFilter normalized = MediaUploaderFilter.defaultIfNull(uploader);
        return files.stream()
            .filter(file -> normalized.includes(file.getUploaderId(), requesterId))
            .toList();
    }

    private void validate(MediaSelection selection) {
        if (selection == null || selection.mode() == null || selection.ids() == null
            || selection.ids().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidMediaSelectionException();
        }
    }
}
