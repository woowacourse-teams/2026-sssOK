package com.sssok.application.mediafolder;

import com.sssok.application.port.out.FileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 담기/꺼내기 공통: 요청받은 mediaIds 중 실제로 존재하는 것과 존재하지 않는 것을 갈라준다.
@Component
@RequiredArgsConstructor
class MediaExistenceResolver {

    private final FileRepository fileRepository;

    MediaExistence resolve(List<Long> mediaIds) {
        List<Long> distinctIds = mediaIds.stream().distinct().toList();
        List<Long> existingIds = fileRepository.findExistingIds(distinctIds);
        List<Long> notFoundIds = distinctIds.stream().filter(id -> !existingIds.contains(id)).toList();
        return new MediaExistence(existingIds, notFoundIds);
    }
}
