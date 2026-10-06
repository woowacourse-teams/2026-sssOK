package com.sssok.application.search;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.MediaDetailAssembler;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MediaSearchQueryRepository;
import com.sssok.application.port.out.MediaSearchQueryRepository.Match;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageSearchResultAssembler {
    private final MediaSearchQueryRepository searchRepository;
    private final FileRepository fileRepository;
    private final MediaDetailAssembler mediaDetailAssembler;

    @Transactional(readOnly = true)
    public List<ImageSearchResult> search(Long roomId, String vector, String model,
                                          int dimensions, double threshold) {
        List<Match> matches = searchRepository.search(roomId, vector, model, dimensions, threshold);
        if (matches.isEmpty()) {
            return List.of();
        }
        List<StoredFile> orderedFiles = findAvailableFiles(roomId, matches);
        List<MediaDetail> details = mediaDetailAssembler.assembleForList(orderedFiles);
        return combineScores(matches, details);
    }

    private List<StoredFile> findAvailableFiles(Long roomId, List<Match> matches) {
        List<Long> mediaIds = matches.stream().map(Match::mediaId).toList();
        List<StoredFile> candidates = fileRepository.findAllByRoomIdAndIdIn(roomId, mediaIds);
        Map<Long, StoredFile> availableFiles = new HashMap<>();
        for (StoredFile file : candidates) {
            if (file.getStatus() == UploadStatus.READY && file.getMediaType().isImage()) {
                availableFiles.put(file.getId(), file);
            }
        }
        // 배치 조회의 반환 순서에 의존하지 않고 유사도 순서를 복원한다.
        List<StoredFile> orderedFiles = new ArrayList<>();
        for (Match match : matches) {
            StoredFile file = availableFiles.get(match.mediaId());
            if (file != null) {
                orderedFiles.add(file);
            }
        }
        return orderedFiles;
    }

    private List<ImageSearchResult> combineScores(List<Match> matches, List<MediaDetail> details) {
        Map<Long, MediaDetail> detailsById = new HashMap<>();
        for (MediaDetail detail : details) {
            detailsById.put(detail.mediaId(), detail);
        }
        List<ImageSearchResult> results = new ArrayList<>();
        for (Match match : matches) {
            MediaDetail detail = detailsById.get(match.mediaId());
            if (detail != null) {
                results.add(new ImageSearchResult(detail, match.similarity()));
            }
        }
        return List.copyOf(results);
    }
}
