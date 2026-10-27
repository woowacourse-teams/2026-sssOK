package com.sssok.renewal.application.download.shared;

import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.download.exception.DownloadFolderNotFoundException;
import com.sssok.renewal.application.download.exception.DownloadMediaNotFoundException;
import com.sssok.renewal.application.download.exception.TooManyDownloadFilesException;
import com.sssok.renewal.outgoing.database.DownloadFileRepository;
import com.sssok.renewal.outgoing.database.DownloadFolderRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 다건 다운로드와 zip 압축의 대상을 정한다. 어느 경로든 원본이 있는 PROCESSING·READY 만 남기고,
// 남은 대상이 없으면 요청한 ID 가 전부 없는 경우와 같은 404 로 본다.
@Component
@RequiredArgsConstructor
public class DownloadTargetResolver {
    private static final int MAX_TARGETS = 1000;

    private final DownloadFileRepository downloadFileRepository;
    private final DownloadFolderRepository downloadFolderRepository;

    public List<StoredFile> resolve(DownloadTargetCommand command) {
        command.validate();
        List<StoredFile> candidates = command.byMediaIds()
            ? findRequestedMedia(command.roomId(), command.mediaIds())
            : findFolderMedia(command.roomId(), command.folderId(), command.requesterId(), command.uploader());
        return requireWithinLimit(requireDownloadable(candidates));
    }

    // 요청 순서를 유지하고 같은 ID 는 한 번만 담는다.
    private List<StoredFile> findRequestedMedia(Long roomId, List<Long> mediaIds) {
        List<Long> distinctIds = mediaIds.stream().distinct().toList();
        requireWithinLimit(distinctIds);
        Map<Long, StoredFile> filesById = downloadFileRepository.findAllByRoomIdAndIdIn(roomId, distinctIds).stream()
            .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return distinctIds.stream().map(filesById::get).filter(Objects::nonNull).toList();
    }

    private List<StoredFile> findFolderMedia(Long roomId, Long folderId, Long requesterId,
                                             MediaUploaderFilter uploader) {
        downloadFolderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new DownloadFolderNotFoundException(folderId));
        MediaUploaderFilter uploaderFilter = MediaUploaderFilter.defaultIfNull(uploader);
        List<Long> folderMediaIds = downloadFolderRepository.findMediaIdsByFolderId(folderId);
        return downloadFileRepository.findAllByIdIn(folderMediaIds).stream()
            .filter(file -> uploaderFilter.includes(file.getUploaderId(), requesterId))
            .toList();
    }

    private List<StoredFile> requireDownloadable(List<StoredFile> files) {
        List<StoredFile> downloadable = files.stream()
            .filter(file -> file.getStatus().isDownloadable())
            .toList();
        if (downloadable.isEmpty()) {
            throw new DownloadMediaNotFoundException();
        }
        return downloadable;
    }

    private <T> List<T> requireWithinLimit(List<T> targets) {
        if (targets.size() > MAX_TARGETS) {
            throw new TooManyDownloadFilesException(MAX_TARGETS);
        }
        return targets;
    }
}
