package com.sssok.renewal.application.media.upload.complete;

import java.util.List;
import com.sssok.application.media.MediaDetail;

public record CompleteUploadResult(List<MediaDetail> registered, List<FailedMedia> failed) {
}
