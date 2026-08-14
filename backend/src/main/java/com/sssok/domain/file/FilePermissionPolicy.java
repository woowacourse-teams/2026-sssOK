package com.sssok.domain.file;

import java.util.List;

public final class FilePermissionPolicy {

    private FilePermissionPolicy() {
    }

    public static boolean canDelete(StoredFile file, Long requesterId, boolean requesterIsHost) {
        return requesterIsHost || file.isUploadedBy(requesterId);
    }

    public static List<StoredFile> filterDeletable(List<StoredFile> files, Long requesterId,
                                                   boolean requesterIsHost) {
        return files.stream()
                .filter(file -> canDelete(file, requesterId, requesterIsHost))
                .toList();
    }
}
