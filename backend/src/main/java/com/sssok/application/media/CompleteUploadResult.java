package com.sssok.application.media;

import java.util.List;

public record CompleteUploadResult(List<MediaDetail> registered, List<FailedMedia> failed) {
}
