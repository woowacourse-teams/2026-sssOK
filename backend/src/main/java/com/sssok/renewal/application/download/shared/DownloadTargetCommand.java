package com.sssok.renewal.application.download.shared;

import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.renewal.application.download.exception.InvalidDownloadParamException;
import java.util.List;

// 다건 다운로드와 zip 압축이 함께 쓰는 대상 지정. mediaIds 와 folderId 중 정확히 하나만 쓰고,
// uploader 는 폴더 전체를 받을 때만 대상을 좁힌다.
public record DownloadTargetCommand(Long roomId, Long requesterId, List<Long> mediaIds, Long folderId,
                                    MediaUploaderFilter uploader) {

    public void validate() {
        boolean bothOrNeither = (mediaIds == null) == (folderId == null);
        if (bothOrNeither || byMediaIds() && uploader != null) {
            throw InvalidDownloadParamException.invalidTarget();
        }
        if (byMediaIds() && mediaIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw InvalidDownloadParamException.invalidMediaIds();
        }
    }

    public boolean byMediaIds() {
        return mediaIds != null;
    }
}
