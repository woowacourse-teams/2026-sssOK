package com.sssok.application.media;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.InvalidPageSizeException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
public class GetMediaListService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final MediaDetailAssembler assembler;
    private final TransactionTemplate readOnlyTransaction;

    public GetMediaListService(FileRepository fileRepository, FolderRepository folderRepository,
                               MediaDetailAssembler assembler, PlatformTransactionManager transactionManager) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.assembler = assembler;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    public List<MediaDetail> list(Long roomId, Long folderId) {
        return list(roomId, folderId, null, MediaUploaderFilter.ALL);
    }

    // 전체 목록은 사진마다 URL 을 두 개씩 서명한다. 300장이면 600번이라 이걸 트랜잭션 안에서 하면
    // 요청마다 DB 커넥션을 수백 ms 쥐게 되고, 동시 요청이 몰리면 커넥션 대기 시간 초과로 500 이 난다(#538).
    // 그래서 DB 조회만 읽기 트랜잭션에서 끝내고 서명은 커넥션을 돌려준 뒤 한다.
    public List<MediaDetail> list(Long roomId, Long folderId, Long requesterId,
                                  MediaUploaderFilter uploader) {
        MediaDetailAssembler.LoadedMedia loaded = readOnlyTransaction.execute(status ->
            assembler.load(find(roomId, folderId, requesterId, uploader), requesterId));
        return assembler.toDetails(loaded);
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

        return new MediaPage(assembler.assembleForList(files, requesterId), nextCursor, hasNext, totalCount);
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

    private List<StoredFile> find(Long roomId, Long folderId, Long requesterId,
                                  MediaUploaderFilter uploader) {
        if (folderId == null) {
            return fileRepository.findAllByRoomIdAndUploaderOrderByNewest(
                roomId, UploadStatus.visibleStatuses(), requesterId, uploader);
        }
        requireFolderInRoom(roomId, folderId);
        return fileRepository.findAllByRoomIdAndFolderIdAndUploaderOrderByNewest(
            roomId, folderId, UploadStatus.visibleStatuses(), requesterId, uploader);
    }

    // 다른 방 폴더로 남의 사진을 들여다보지 못하도록 소속까지 확인한다.
    private void requireFolderInRoom(Long roomId, Long folderId) {
        folderRepository.findById(folderId)
            .filter(folder -> folder.belongsTo(roomId))
            .orElseThrow(() -> new FolderNotFoundException(folderId));
    }
}
