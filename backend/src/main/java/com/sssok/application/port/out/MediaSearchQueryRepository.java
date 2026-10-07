package com.sssok.application.port.out;

import java.util.List;
import com.sssok.application.media.MediaUploaderFilter;

public interface MediaSearchQueryRepository {
    List<Match> search(Long roomId, String vector, String model, int dimensions, double threshold,
        Long folderId, Long requesterId, MediaUploaderFilter uploader);

    record Match(Long mediaId, double similarity) {
    }
}
