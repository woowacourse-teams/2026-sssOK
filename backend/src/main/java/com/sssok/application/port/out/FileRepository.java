package com.sssok.application.port.out;

import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.application.media.MediaUploaderFilter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 파일 영속화 출력
public interface FileRepository {

    StoredFile save(StoredFile storedFile);

    List<StoredFile> saveAll(List<StoredFile> storedFiles);

    Optional<StoredFile> findById(Long id);

    // 삭제 트랜잭션과 순서를 강제하는 잠금 조회. 썸네일 워커가 완료를 반영하기 직전에 쓴다.
    Optional<StoredFile> findByIdForUpdate(Long id);

    List<StoredFile> findAllByIdIn(List<Long> ids);

    // 담기/꺼내기에서 어떤 mediaId가 실제로 존재하는지 확인하는 용도.
    // 넘긴 id 중 존재하는 것만 골라 반환한다 — 나머지는 notFoundMediaIds로 취급하면 된다.
    List<Long> findExistingIds(List<Long> ids);

    List<StoredFile> findAllByRoomId(Long roomId);

    List<StoredFile> findAllByRoomIdAndIdIn(Long roomId, Collection<Long> ids);

    List<StoredFile> findAllByRoomIdAndIdNotIn(Long roomId, Collection<Long> ids);

    // 조회 API 전용. 같은 업로드 요청에 묶인 파일은 createdAt 이 전부 같아서, id 까지 봐야
    // 순서가 호출마다 흔들리지 않는다.
    List<StoredFile> findAllByRoomIdAndStatusInOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses);

    List<StoredFile> findAllByRoomIdAndUploaderOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader);

    List<StoredFile> findPageByRoomIdAndStatusInOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses, Instant lastCreatedAt,
        Long lastMediaId, int limit);

    long countByRoomIdAndStatusIn(Long roomId, Collection<UploadStatus> statuses);

    List<StoredFile> findPageByRoomIdAndUploaderOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader, Instant lastCreatedAt, Long lastMediaId, int limit);

    long countByRoomIdAndUploader(
        Long roomId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader);

    // 위와 같지만 대상을 주어진 id 로 한정한다(폴더 필터).
    List<StoredFile> findAllByRoomIdAndIdInAndStatusInOrderByNewest(
        Long roomId, Collection<Long> ids, Collection<UploadStatus> statuses);

    List<StoredFile> findAllByRoomIdAndFolderIdAndUploaderOrderByNewest(
        Long roomId, Long folderId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader);

    List<StoredFile> findPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
        Long roomId, Long folderId, Collection<UploadStatus> statuses,
        Instant lastCreatedAt, Long lastMediaId, int limit);

    long countByRoomIdAndFolderIdAndStatusIn(
        Long roomId, Long folderId, Collection<UploadStatus> statuses);

    List<StoredFile> findPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
        Long roomId, Long folderId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader, Instant lastCreatedAt, Long lastMediaId, int limit);

    long countByRoomIdAndFolderIdAndUploader(
        Long roomId, Long folderId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader);

    // 썸네일 회수 배치용. 이 시각보다 오래 PROCESSING 에 남아 있는 미디어를 오래된 순으로 준다.
    // 종류까지 주는 이유: 회수도 사진과 영상이 서로 다른 워커 풀에서 돌아야 한다.
    List<StuckMedia> findStuckInProcessing(Instant stuckBefore, int limit);

    record StuckMedia(Long mediaId, MediaType mediaType) {

        public boolean isVideo() {
            return mediaType.isVideo();
        }
    }

    void deleteAllByRoomId(Long roomId);

    void deleteAllByIdIn(List<Long> ids);
}
