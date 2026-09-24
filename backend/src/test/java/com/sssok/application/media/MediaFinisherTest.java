package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 워커가 도는 사이 사용자가 같은 미디어를 지우거나 폴더에 담는 경합을 확인한다.
@SpringBootTest
class MediaFinisherTest extends PostgresContainerSupport {

    private static final Long ROOM_ID = 255L;
    private static final Long UPLOADER_ID = 255L;

    @Autowired
    MediaFinisher mediaFinisher;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @MockitoBean
    OrphanObjectCollector orphanObjectCollector;

    @MockitoBean
    EventPublisherPort eventPublisherPort;

    @MockitoBean
    EventSubscriberPort eventSubscriberPort;

    // 커밋된 결과를 봐야 해서 @Transactional 을 걸 수 없다. 남긴 행은 직접 치운다 —
    // 두면 고정 id 로 미디어를 넣는 다른 테스트와 id 가 부딪힌다.
    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Long> mediaIds = fileRepository.findAllByRoomId(ROOM_ID).stream()
                .map(StoredFile::getId)
                .toList();
            folderMediaRepository.detachFromAllFolders(mediaIds);
            fileRepository.deleteAllByIdIn(mediaIds);
        });
    }

    @Test
    void 처리가_끝나면_READY로_넘기고_media_ready를_발행한다() {
        StoredFile file = processing();

        mediaFinisher.finish(file.getId(), processed(file));

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.READY);
        verify(eventPublisherPort).publish(eq(ROOM_ID), eq("media.ready"), any());
    }

    @Test
    void 지워진_미디어는_되살리지_않고_이벤트도_내보내지_않는다() {
        StoredFile file = processing();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        mediaFinisher.finish(file.getId(), processed(file));

        // 되살아난다면 새 id 로 들어오므로 id 가 아니라 방 전체로 확인한다.
        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).isEmpty();
        verify(eventPublisherPort, never()).publish(any(), anyString(), any());
    }

    // 행이 없으면 ThumbnailSweeper 도 못 찾아 R2 에 영영 남는다. 트랜잭션 안에서 직접 지우지 않고
    // 회수 대기열에 남겨 커밋 뒤 정리한다.
    @Test
    void 지워진_미디어면_방금_올린_썸네일을_정리_대기열에_남긴다() {
        StoredFile file = processing();
        StorageKey thumbnailKey = file.getStorageKey().thumbnail();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        mediaFinisher.finish(file.getId(), processed(file));

        verify(orphanObjectCollector).enqueue(List.of(thumbnailKey));
        verify(fileStoragePort, never()).delete(any());
    }

    // ThumbnailSweeper 는 분산 락도 중복 실행 방지도 없어 같은 미디어에 워커를 하나 더 태울 수 있다.
    // 두 워커의 썸네일 키는 원본에서 유도되어 같으므로, 늦게 끝난 쪽이 지우면 살아 있는 행의 썸네일이 사라진다.
    @Test
    void 다른_워커가_먼저_끝냈으면_썸네일을_지우지_않는다() {
        StoredFile file = processing();
        mediaFinisher.finish(file.getId(), processed(file));

        mediaFinisher.finish(file.getId(), processed(file));

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getThumbnailKey())
            .isEqualTo(file.getStorageKey().thumbnail());
        verify(fileStoragePort, never()).delete(any());
    }

    @Test
    void 지워진_미디어는_실패_처리로도_되살아나지_않는다() {
        StoredFile file = processing();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        mediaFinisher.markFailed(file.getId());

        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).isEmpty();
    }

    // 워커 트랜잭션이 커밋되기 전에 폴더 담기가 끼어드는 상황.
    // payload 를 미리 만들면 낡은 folderIds 가 실려 나가 방금 담은 폴더가 화면에서 사라진다.
    @Test
    void media_ready_payload는_커밋_시점의_폴더_소속을_담는다() {
        StoredFile file = processing();
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            mediaFinisher.finish(file.getId(), processed(file));
            folderMediaRepository.attachToFolder(folder.getId(), List.of(file.getId()));
        });

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisherPort).publish(eq(ROOM_ID), eq("media.ready"), payload.capture());
        assertThat(((MediaDetail) payload.getValue()).folderIds()).containsExactly(folder.getId());
    }

    private StoredFile processing() {
        StoredFile file = StoredFile.reserve(
            ROOM_ID, UPLOADER_ID, "사진.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        file.startProcessing();
        return fileRepository.save(file);
    }

    private ProcessedMedia processed(StoredFile file) {
        return ProcessedMedia.ofImage(file.getStorageKey().thumbnail(), null, 1200, 900, null, null);
    }
}
