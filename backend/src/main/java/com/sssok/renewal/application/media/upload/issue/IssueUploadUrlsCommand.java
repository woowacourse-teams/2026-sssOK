package com.sssok.renewal.application.media.upload.issue;

import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import java.util.List;

public record IssueUploadUrlsCommand(Long roomId, Long uploaderId, List<UploadCandidate> files, List<Long> folderIds) {
    public void validate() {
        if (files == null || files.isEmpty()) {
            throw InvalidUploadRequestException.emptyFiles();
        }
        if (files.stream().anyMatch(file -> file == null)) {
            throw InvalidUploadRequestException.missingFile();
        }
    }

}
