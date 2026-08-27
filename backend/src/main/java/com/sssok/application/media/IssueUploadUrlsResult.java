package com.sssok.application.media;

import java.util.List;

public record IssueUploadUrlsResult(List<UploadUrl> issued, List<RejectedFile> rejected) {
}
