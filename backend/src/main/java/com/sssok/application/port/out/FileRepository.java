package com.sssok.application.port.out;

import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
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


    void deleteAllByRoomId(Long roomId);
}
