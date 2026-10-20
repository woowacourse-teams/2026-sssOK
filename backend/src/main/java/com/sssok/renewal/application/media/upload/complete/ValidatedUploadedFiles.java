package com.sssok.renewal.application.media.upload.complete;

import com.sssok.domain.file.StoredFile;
import java.util.List;

public record ValidatedUploadedFiles(List<StoredFile> verifiedFiles, List<FailedMedia> failedMedia) { }
