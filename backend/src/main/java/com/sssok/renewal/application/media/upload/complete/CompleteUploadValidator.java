package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.Stage.*;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.domain.media.upload.UploadRejectionReason;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompleteUploadValidator {
    private final UploadRegistrationMetrics metrics;
    private final UploadFileRepository uploadFileRepository;
    private final FileStorage fileStorage;
    private final UploadHeadWorkers headWorkers;

    public ValidatedUploadedFiles validate(CompleteUploadCommand command) {
        List<StoredFile> foundFiles = metrics.measure(FILE_LOOKUP,
            () -> uploadFileRepository.findAllByIdIn(command.mediaIds()));
        rejectOthersReservation(foundFiles, command.uploaderId());

        // 요청 순서를 유지한다. DB 에 없는 ID 는 null 로 남겨 MEDIA_NOT_FOUND 로 거절한다.
        Map<Long, StoredFile> filesById = foundFiles.stream()
            .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        List<StoredFile> requestedFiles = command.mediaIds().stream().map(filesById::get).toList();
        List<UploadRejectionReason> rejections = headWorkers.map(requestedFiles,
            file -> file == null ? UploadRejectionReason.MEDIA_NOT_FOUND : verifyUploaded(file));

        List<StoredFile> verifiedFiles = new ArrayList<>();
        List<FailedMedia> failedMedia = new ArrayList<>();
        for (int i = 0; i < requestedFiles.size(); i++) {
            UploadRejectionReason rejection = rejections.get(i);
            if (rejection == null) {
                verifiedFiles.add(requestedFiles.get(i));
            } else {
                failedMedia.add(FailedMedia.of(command.mediaIds().get(i), rejection));
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
        Optional<UploadedObject> uploaded = metrics.measure(STORAGE_HEAD,
            () -> fileStorage.findUploaded(file.getStorageKey()));
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

}
