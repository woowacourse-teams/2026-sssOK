package com.sssok.renewal.application.media.upload.issue;

import com.sssok.renewal.application.media.upload.shared.UploadUrl;
import java.util.List;

public record IssueUploadUrlsResult(List<UploadUrl> issued, List<RejectedFile> rejected) { }
