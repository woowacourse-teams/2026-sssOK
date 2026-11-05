package com.sssok.renewal.application.organization.job;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 요청 시점의 방에서 분석할 수 있는 사진을 고른다. 이후 업로드는 이 선택에 들어오지 않는다.
// 이미지이고 Python 이 받는 확장자(jpg·png)이며 업로드가 끝난(READY) 사진만 대상이다.
// 동영상·GIF·처리 중 사진은 제외하고 개수만 센다.
@Component
@RequiredArgsConstructor
public class OrganizeTargetPhotoSelector {
    private final FileRepository fileRepository;

    public record EligiblePhoto(long mediaId, String extension) {
    }

    public record Selection(List<EligiblePhoto> photos, int excludedCount) {
    }

    @Transactional(readOnly = true)
    public Selection select(Long roomId) {
        List<StoredFile> visible = fileRepository.findAllByRoomIdAndStatusInOrderByNewest(
            roomId, UploadStatus.visibleStatuses());
        List<EligiblePhoto> photos = visible.stream()
            .filter(this::isAnalyzable)
            .sorted(Comparator.comparing(StoredFile::getId))
            .map(file -> new EligiblePhoto(file.getId(), file.getMediaType().extension()))
            .toList();
        return new Selection(photos, visible.size() - photos.size());
    }

    private boolean isAnalyzable(StoredFile file) {
        MediaType type = file.getMediaType();
        return file.getStatus() == UploadStatus.READY && (type == MediaType.JPEG || type == MediaType.PNG);
    }
}
