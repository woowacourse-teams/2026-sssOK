package com.sssok.application.port.out;

import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 파일 영속화 출력
public interface FileRepository {

    StoredFile save(StoredFile storedFile);

    List<StoredFile> saveAll(List<StoredFile> storedFiles);

    Optional<StoredFile> findById(Long id);

    List<StoredFile> findAllByIdIn(List<Long> ids);

    // 담기/꺼내기에서 어떤 mediaId가 실제로 존재하는지 확인하는 용도.
    // 넘긴 id 중 존재하는 것만 골라 반환한다 — 나머지는 notFoundMediaIds로 취급하면 된다.
    List<Long> findExistingIds(List<Long> ids);

    List<StoredFile> findAllByRoomId(Long roomId);

    // 조회 API 전용. 같은 업로드 요청에 묶인 파일은 createdAt 이 전부 같아서, id 까지 봐야
    // 순서가 호출마다 흔들리지 않는다.
    List<StoredFile> findAllByRoomIdAndStatusInOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses);

    // 위와 같지만 대상을 주어진 id 로 한정한다(폴더 필터).
    List<StoredFile> findAllByRoomIdAndIdInAndStatusInOrderByNewest(
        Long roomId, Collection<Long> ids, Collection<UploadStatus> statuses);

    // 썸네일 회수 배치용. 이 시각보다 오래 PROCESSING 에 남아 있는 미디어 id 를 오래된 순으로 준다.
    List<Long> findStuckInProcessing(Instant stuckBefore, int limit);

    void deleteAllByRoomId(Long roomId);

    void deleteAllByIdIn(List<Long> ids);
}
