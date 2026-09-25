package com.sssok.application.media;

import com.sssok.domain.file.StoredFile;
import java.util.List;

public record ResolvedMediaIds(List<StoredFile> files, List<Long> notFoundIds) {
}
