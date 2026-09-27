package com.sssok.application.download;

import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.MediaIdsResolver;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// zip 압축 다운로드(POST /rooms/{roomId}/downloads/zip)와 다건 다운로드(POST .../downloads/batch)의 대상을 정한다.
// mediaIds/folderId 중 정확히 하나만 쓸 수 있다.
// 어느 경로든 마지막엔 UploadStatus.isDownloadable() 인 것만 남긴다.
// 대상이 하나도 없으면 404로 본다 — 요청한 id가 전부 없는 경우와 같은 경로로 처리한다.
@Component
@RequiredArgsConstructor
class DownloadTargetResolver {

    private static final int MAX_MEDIA_IDS = 1000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final MediaIdsResolver mediaIdsResolver;

    List<StoredFile> resolve(Long roomId, List<Long> mediaIds, Long folderId,
                             Long requesterId, MediaUploaderFilter uploader) {
        if (mediaIds != null && folderId != null || mediaIds == null && folderId == null
            || mediaIds != null && uploader != null) {
            throw new InvalidDownloadParamException();
        }
        if (mediaIds != null) {
            List<StoredFile> selected =
                mediaIdsResolver.resolve(roomId, mediaIds, MAX_MEDIA_IDS).files();
            return requireWithinLimit(requireDownloadable(selected));
        }
        return requireWithinLimit(requireDownloadable(filterByUploader(
            resolveByFolderId(roomId, folderId), requesterId, uploader)));
    }

    private List<StoredFile> resolveByFolderId(Long roomId, Long folderId) {
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));

        List<Long> mediaIds = folderMediaRepository.findMediaIdsByFolderId(folderId);
        return fileRepository.findAllByIdIn(mediaIds);
    }

    private List<StoredFile> requireDownloadable(List<StoredFile> files) {
        List<StoredFile> downloadable = files.stream()
            .filter(file -> file.getStatus().isDownloadable())
            .toList();
        if (downloadable.isEmpty()) {
            throw new MediaNotFoundException();
        }
        return downloadable;
    }

    private List<StoredFile> requireWithinLimit(List<StoredFile> files) {
        if (files.size() > MAX_MEDIA_IDS) {
            throw new TooManyFilesException(MAX_MEDIA_IDS);
        }
        return files;
    }

    private List<StoredFile> filterByUploader(List<StoredFile> files, Long requesterId,
                                              MediaUploaderFilter uploader) {
        MediaUploaderFilter normalized = MediaUploaderFilter.defaultIfNull(uploader);
        return files.stream()
            .filter(file -> normalized.includes(file.getUploaderId(), requesterId))
            .toList();
    }
}
