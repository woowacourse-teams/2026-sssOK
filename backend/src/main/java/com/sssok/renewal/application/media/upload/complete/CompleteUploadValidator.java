package com.sssok.renewal.application.media.upload.complete;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.domain.media.upload.UploadRejectionReason;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompleteUploadValidator {
    private final UploadFileRepository uploadFileRepository;
    private final FileStorage fileStorage;

    public ValidatedUploadedFiles validate(CompleteUploadCommand command) {
        List<StoredFile> foundFiles = uploadFileRepository.findAllByIdIn(command.mediaIds());
        rejectOthersReservation(foundFiles, command.uploaderId());

        List<StoredFile> verifiedFiles = new ArrayList<>();
        List<FailedMedia> failedMedia = new ArrayList<>();
        for (Long mediaId : command.mediaIds()) {
            StoredFile file = findIn(foundFiles, mediaId);
            if (file == null) {
                failedMedia.add(FailedMedia.of(mediaId, UploadRejectionReason.MEDIA_NOT_FOUND));
                continue;
            }
            UploadRejectionReason reason = verifyUploaded(file);
            if (reason == null) {
                verifiedFiles.add(file);
            } else {
                failedMedia.add(FailedMedia.of(mediaId, reason));
            }
        }

        return new ValidatedUploadedFiles(verifiedFiles, failedMedia);
    }

    // 남이 예약한 mediaId 가 하나라도 섞여 있으면 요청 전체를 막는다. 남의 업로드를 건드리는 시도다.
    private void rejectOthersReservation(List<StoredFile> foundFiles, Long uploaderId) {
        boolean hasOthers = foundFiles.stream().anyMatch(file -> !file.isUploadedBy(uploaderId));
        if (hasOthers) {
            throw new MediaForbiddenException();
        }
    }

    // 발급 때 신고한 값과 실제로 올라온 것이 다르면 거부한다. 통과하면 null 이다.
    private UploadRejectionReason verifyUploaded(StoredFile file) {
        Optional<UploadedObject> uploaded = fileStorage.findUploaded(file.getStorageKey());
        if (uploaded.isEmpty()) {
            return UploadRejectionReason.UPLOAD_NOT_COMPLETED;
        }
        UploadedObject uploadedObject = uploaded.get();
        if (!file.matchesUploaded(uploadedObject.sizeBytes(), uploadedObject.contentType())) {
            return uploadedObject.sizeBytes() != file.getFileSize().bytes()
                ? UploadRejectionReason.FILE_TOO_LARGE
                : UploadRejectionReason.UNSUPPORTED_MEDIA_TYPE;
        }
        return null;
    }

    private StoredFile findIn(List<StoredFile> files, Long mediaId) {
        return files.stream()
            .filter(file -> file.getId().equals(mediaId))
            .findFirst()
            .orElse(null);
    }

}
