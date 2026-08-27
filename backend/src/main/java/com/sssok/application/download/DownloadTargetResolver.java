package com.sssok.application.download;

import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// zip 압축 다운로드(POST /rooms/{roomId}/downloads)의 대상을 정한다.
// mediaIds/folderId 중 하나만 쓸 수 있고, 둘 다 생략하면 방 전체가 대상이다.
// 어느 경로든 마지막엔 READY 상태만 남긴다 — PROCESSING/RESERVED/FAILED는 실물이 없거나
// 아직 압축할 수 없는 상태라 대상에서 빠지고, 그 결과 대상이 하나도 없으면 404로 본다
// (요청한 id가 전부 없는 경우와, 존재는 하지만 전부 READY가 아닌 경우를 같은 경로로 처리).
@Component
@RequiredArgsConstructor
class DownloadTargetResolver {

    private static final int MAX_MEDIA_IDS = 1000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;

    List<StoredFile> resolve(Long roomId, List<Long> mediaIds, Long folderId) {
        if (mediaIds != null && folderId != null) {
            throw new InvalidDownloadParamException();
        }
        if (mediaIds != null) {
            return resolveByMediaIds(roomId, mediaIds);
        }
        if (folderId != null) {
            return resolveByFolderId(roomId, folderId);
        }
        return requireReady(fileRepository.findAllByRoomId(roomId));
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
        return requireReady(inRoom);
    }

    private List<StoredFile> resolveByFolderId(Long roomId, Long folderId) {
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));

        List<Long> mediaIds = folderMediaRepository.findMediaIdsByFolderId(folderId);
        return requireReady(fileRepository.findAllByIdIn(mediaIds));
    }

    private List<StoredFile> requireReady(List<StoredFile> files) {
        List<StoredFile> ready = files.stream()
            .filter(file -> file.getStatus() == UploadStatus.READY)
            .toList();
        if (ready.isEmpty()) {
            throw new MediaNotFoundException();
        }
        return ready;
    }
}
