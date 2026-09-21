package com.sssok.application.media;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.InvalidPageSizeException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetMediaListService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final MediaDetailAssembler assembler;

    @Transactional(readOnly = true)
    public List<MediaDetail> list(Long roomId, Long folderId) {
        return assembler.assemble(find(roomId, folderId));
    }

    @Transactional(readOnly = true)
    public MediaPage page(Long roomId, Long folderId, int size, MediaCursor cursor) {
        return page(roomId, folderId, null, MediaUploaderFilter.ALL, size, cursor);
    }

    @Transactional(readOnly = true)
    public MediaPage page(Long roomId, Long folderId, Long requesterId,
                          MediaUploaderFilter uploader, int size, MediaCursor cursor) {
        requireValidSize(size);
        if (folderId != null) {
            requireFolderInRoom(roomId, folderId);
        }
        List<StoredFile> candidates = findPage(
            roomId, folderId, requesterId, uploader, size, cursor);

        boolean hasNext = candidates.size() > size;
        List<StoredFile> files = candidates.subList(0, Math.min(size, candidates.size()));
        MediaCursor nextCursor = hasNext
            ? nextCursor(roomId, folderId, requesterId, uploader, files)
            : null;
        long totalCount = count(roomId, folderId, requesterId, uploader);

        return new MediaPage(assembler.assemble(files), nextCursor, hasNext, totalCount);
    }

    private List<StoredFile> findPage(Long roomId, Long folderId, Long requesterId,
                                      MediaUploaderFilter uploader, int size, MediaCursor cursor) {
        if (folderId == null) {
            return fileRepository.findPageByRoomIdAndUploaderOrderByNewest(
                roomId, UploadStatus.visibleStatuses(), requesterId, uploader,
                cursor == null ? null : cursor.lastCreatedAt(),
                cursor == null ? null : cursor.lastMediaId(), size + 1);
        }
        return fileRepository.findPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
            roomId, folderId, UploadStatus.visibleStatuses(), requesterId, uploader,
            cursor == null ? null : cursor.lastCreatedAt(),
            cursor == null ? null : cursor.lastMediaId(), size + 1);
    }

    private long count(Long roomId, Long folderId, Long requesterId,
                       MediaUploaderFilter uploader) {
        if (folderId == null) {
            return fileRepository.countByRoomIdAndUploader(
                roomId, UploadStatus.visibleStatuses(), requesterId, uploader);
        }
        return fileRepository.countByRoomIdAndFolderIdAndUploader(
            roomId, folderId, UploadStatus.visibleStatuses(), requesterId, uploader);
    }

    private MediaCursor nextCursor(Long roomId, Long folderId, Long requesterId,
                                   MediaUploaderFilter uploader, List<StoredFile> files) {
        StoredFile last = files.getLast();
        return new MediaCursor(
            roomId, folderId, requesterId, uploader, last.getCreatedAt(), last.getId());
    }

    private void requireValidSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException();
        }
    }

    private List<StoredFile> find(Long roomId, Long folderId) {
        if (folderId == null) {
            return fileRepository.findAllByRoomIdAndStatusInOrderByNewest(
                roomId, UploadStatus.visibleStatuses());
        }
        requireFolderInRoom(roomId, folderId);
        // 방 조건을 여기서도 건다. 폴더가 이 방 소속인 건 확인했지만, 담긴 미디어까지
        // 같은 방인지는 보장되지 않아서다.
        return fileRepository.findAllByRoomIdAndIdInAndStatusInOrderByNewest(
            roomId, folderMediaRepository.findMediaIdsByFolderId(folderId),
            UploadStatus.visibleStatuses());
    }

    // 다른 방 폴더로 남의 사진을 들여다보지 못하도록 소속까지 확인한다.
    private void requireFolderInRoom(Long roomId, Long folderId) {
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));
    }
}
