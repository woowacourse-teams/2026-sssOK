package com.sssok.renewal.application.media.upload.issue;

import com.sssok.domain.file.StoredFile;
import java.util.List;

public record ValidatedUploadFiles(List<StoredFile> acceptedFiles, List<RejectedFile> rejectedFiles) {
    public ValidatedUploadFiles {
        acceptedFiles = List.copyOf(acceptedFiles);
        rejectedFiles = List.copyOf(rejectedFiles);
    }
}
