package com.sssok.renewal.application.media.upload.issue;

import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.config.UploadProperties;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.UploadSizePolicy;
import com.sssok.domain.file.exception.InvalidFileSizeException;
import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.renewal.domain.media.exception.InvalidUploadFileException;
import com.sssok.renewal.domain.media.exception.UnsupportedUploadTypeException;
import com.sssok.renewal.domain.media.exception.UploadSizeExceededException;
import com.sssok.renewal.domain.media.exception.UploadRejectedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UploadFileValidator {
    private final UploadProperties uploadProperties;

    public ValidatedUploadFiles validate(IssueUploadUrlsCommand command) {
        return validate(command, null);
    }

    public ValidatedUploadFiles validate(IssueUploadUrlsCommand command, String uploaderNickname) {
        Instant uploadRequestedAt = Instant.now();
        List<StoredFile> acceptedFiles = new ArrayList<>();
        List<RejectedFile> rejectedFiles = new ArrayList<>();
        for (UploadCandidate requestedFile : command.files()) {
            try {
                acceptedFiles.add(createPendingUpload(command.roomId(), command.uploaderId(),
                    requestedFile.fileName(), requestedFile.mimeType(), requestedFile.size(),
                    uploadRequestedAt, uploadProperties.sizePolicy(), uploaderNickname));
            } catch (UploadRejectedException exception) {
                rejectedFiles.add(RejectedFile.from(requestedFile, exception));
            }
        }
        return new ValidatedUploadFiles(acceptedFiles, rejectedFiles);
    }

    private void validateMetadata(String fileName, Long fileSizeBytes) {
        if (fileName == null || fileName.isBlank() || fileSizeBytes == null) {
            throw new InvalidUploadFileException();
        }
    }

    private StoredFile createPendingUpload(Long roomId, Long uploaderId, String fileName, String mimeType,
                                               Long fileSizeBytes, Instant uploadRequestedAt, UploadSizePolicy uploadSizePolicy, String uploaderNickname) {
        validateMetadata(fileName, fileSizeBytes);
        try {
            return StoredFile.reserve(roomId, uploaderId, fileName, mimeType, new FileSize(fileSizeBytes),
                uploadRequestedAt, uploadSizePolicy, uploaderNickname);
        } catch (InvalidFileSizeException e) {
            throw new InvalidUploadFileException();
        } catch (UnsupportedMediaTypeException e) {
            throw new UnsupportedUploadTypeException();
        } catch (FileSizeExceededException e) {
            throw new UploadSizeExceededException();
        }
    }
}
