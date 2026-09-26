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

    // 폴더 담기·꺼내기용. 목록에 보이는 미디어만 남긴다 — 실물이 없는 RESERVED·FAILED 를 담으면
    // 응답의 updatedCount 가 폴더 photoCount 와 어긋난다. 목록에서 고른 뒤 요청이 닿기 전에
    // 그 미디어가 FAILED 로 확정되는 경우가 있어, 클라이언트가 id 를 직접 보내도 한 번 더 거른다.
    // 삭제는 실물 없는 행도 정리해야 하므로 이 필터를 쓰지 않는다.
    public ResolvedMediaIds resolveVisible(Long roomId, List<Long> mediaIds) {
        ResolvedMediaIds resolved = resolve(roomId, mediaIds);
        return new ResolvedMediaIds(
            resolved.files().stream().filter(file -> file.getStatus().isVisible()).toList(),
            resolved.notFoundIds());
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
