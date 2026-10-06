package com.sssok.application.search;

import com.sssok.application.media.MediaDetailAssembler;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MediaSearchQueryRepository;
import com.sssok.application.port.out.MediaSearchQueryRepository.Match;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageSearchResultAssembler {
    private final MediaSearchQueryRepository searchRepository;
    private final FileRepository files;
    private final MediaDetailAssembler assembler;

    @Transactional(readOnly = true)
    public List<ImageSearchResult> search(Long roomId, String vector, String model,
                                          int dimensions, double threshold) {
        List<Match> matches = searchRepository.search(roomId, vector, model, dimensions, threshold);
        if (matches.isEmpty()) {
            return List.of();
        }
        var candidates = files.findAllByRoomIdAndIdIn(roomId, matches.stream().map(Match::mediaId).toList())
            .stream().filter(file -> file.getStatus() == UploadStatus.READY && file.getMediaType().isImage())
            .collect(Collectors.toMap(file -> file.getId(), Function.identity()));
        var ordered = matches.stream().map(match -> candidates.get(match.mediaId()))
            .filter(java.util.Objects::nonNull).toList();
        var details = assembler.assembleForList(ordered).stream()
            .collect(Collectors.toMap(detail -> detail.mediaId(), Function.identity()));
        return matches.stream().filter(match -> details.containsKey(match.mediaId()))
            .map(match -> new ImageSearchResult(details.get(match.mediaId()), match.similarity())).toList();
    }
}
