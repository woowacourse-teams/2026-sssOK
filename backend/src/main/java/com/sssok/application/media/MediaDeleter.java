package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 트랜잭션 안에서 스토리지를 건드리지 않는다. deleteObject 는 한 번이 HTTP 요청 한 번이라
// 500 장 삭제가 최대 1000 회의 직렬 왕복이 되고, 그동안 DB 커넥션을 쥐고 있으면 동시 삭제 몇 건에
// 풀이 말라 삭제와 무관한 API 까지 함께 느려진다
// (CompleteUploadService·GenerateThumbnailService 가 같은 이유로 트랜잭션을 열지 않는다).
//
// 대신 지울 키를 정리 대기열에 남기고 커밋한다. 사용자 기준 삭제 완료는 DB 행이 지워진 시점이고,
// 실물 정리는 StorageCleanupTrigger 가 커밋 뒤 비동기로 한다. 그 정리가 실패해도 대기열 행이
// 남아 OrphanObjectSweeper 가 회수하므로, 삭제 자체와 media.deleted 는 그대로 완료된다.
@Component
@RequiredArgsConstructor
public class MediaDeleter {

    private final FileRepository fileRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final OrphanObjectCollector orphanObjectCollector;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void delete(Long roomId, List<StoredFile> files) {
        List<StorageKey> orphaned = orphanedKeysOf(files);
        orphanObjectCollector.enqueue(orphaned);

        List<Long> mediaIds = files.stream().map(StoredFile::getId).toList();
        folderMediaRepository.detachFromAllFolders(mediaIds);
        fileRepository.deleteAllByIdIn(mediaIds);
        eventPublisher.publishEvent(new MediaDeletedEvent(roomId, mediaIds));
        eventPublisher.publishEvent(new ObjectsOrphanedEvent(orphaned));
    }

    // 썸네일 키는 저장된 값이 아니라 원본 키에서 유도한다 — 만드는 곳이
    // GenerateThumbnailService 한 곳뿐이라 항상 같은 값이다.
    //
    // thumbnailKey 가 채워졌는지 보고 넘기면, 아직 PROCESSING 인 미디어를 지울 때 이 트랜잭션이
    // 읽은 값은 null 인데 워커가 그 사이 썸네일을 올려 스토리지에 고아가 남는다 (#255).
    // 유도한 키를 늘 넣어두면 없는 키를 지우는 셈이 될 뿐이고, 그건 성공으로 치는 계약이다.
    private List<StorageKey> orphanedKeysOf(List<StoredFile> files) {
        List<StorageKey> keys = new ArrayList<>(files.size() * 2);
        for (StoredFile file : files) {
            keys.add(file.getStorageKey());
            keys.add(file.getStorageKey().thumbnail());
        }
        return List.copyOf(keys);
    }
}
