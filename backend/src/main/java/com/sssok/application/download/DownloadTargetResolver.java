package com.sssok.application.download;

import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.MediaSelection;
import com.sssok.application.media.MediaSelectionResolver;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// zip 압축 다운로드(POST /rooms/{roomId}/downloads/zip)와 다건 다운로드(POST .../downloads/batch)의 대상을 정한다.
// mediaIds/folderId 중 하나만 쓸 수 있고, 둘 다 생략하면 방 전체가 대상이다.
// 어느 경로든 마지막엔 UploadStatus.isDownloadable() 인 것만 남긴다.
// 대상이 하나도 없으면 404로 본다 — 요청한 id가 전부 없는 경우와 같은 경로로 처리한다.
@Component
@RequiredArgsConstructor
class DownloadTargetResolver {

    private static final int MAX_MEDIA_IDS = 1000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final MediaSelectionResolver mediaSelectionResolver;

    List<StoredFile> resolveSelection(Long roomId, MediaSelection selection, Long folderId,
                                      Long requesterId, MediaUploaderFilter uploader) {
        if (selection != null && folderId != null || selection == null && folderId == null) {
            throw new InvalidDownloadParamException();
        }
        if (selection != null) {
            List<StoredFile> selected =
                mediaSelectionResolver.resolve(roomId, selection, requesterId, uploader).files();
            if (selected.size() > MAX_MEDIA_IDS) {
                throw new TooManyFilesException(MAX_MEDIA_IDS);
            }
            return requireDownloadable(selected);
        }
        return requireDownloadable(mediaSelectionResolver.filterByUploader(
            resolveByFolderId(roomId, folderId), requesterId, uploader));
    }

    List<StoredFile> resolve(Long roomId, List<Long> mediaIds, Long folderId) {
        if (mediaIds != null && folderId != null) {
            throw new InvalidDownloadParamException();
        }
        if (mediaIds != null) {
            return resolveByMediaIds(roomId, mediaIds);
        }
        if (folderId != null) {
            return requireDownloadable(resolveByFolderId(roomId, folderId));
        }
        return requireDownloadable(fileRepository.findAllByRoomId(roomId));
    }

    private List<StoredFile> resolveByMediaIds(Long roomId, List<Long> mediaIds) {
        List<Long> distinctIds = mediaIds.stream().distinct().toList();
        if (distinctIds.size() > MAX_MEDIA_IDS) {
            throw new TooManyFilesException(MAX_MEDIA_IDS);
        }

        // 다른 방 미디어는 존재 자체를 드러내지 않는다 — 대상에서 조용히 빠진다.
        List<StoredFile> inRoom = fileRepository.findAllByIdIn(distinctIds).stream()
            .filter(file -> file.getRoomId().equals(roomId))
            .toList();
        return requireDownloadable(inRoom);
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
}
