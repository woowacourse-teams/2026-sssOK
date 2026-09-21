package com.sssok.application.media;

import com.sssok.domain.file.StoredFile;
import java.util.List;

public record ResolvedMediaSelection(List<StoredFile> files, List<Long> notFoundIds) {
}
