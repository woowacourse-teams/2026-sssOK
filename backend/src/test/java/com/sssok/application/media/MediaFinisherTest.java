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
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
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
    EventPublisherPort eventPublisherPort;

    @MockitoBean
    EventSubscriberPort eventSubscriberPort;

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
        int before = fileRepository.findAllByRoomId(ROOM_ID).size();

        mediaFinisher.finish(file.getId(), processed(file));

        // 되살아난다면 새 id 로 들어오므로 id 가 아니라 방의 행 수로 확인한다.
        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).hasSize(before);
        verify(eventPublisherPort, never()).publish(any(), anyString(), any());
    }

    // 행이 없으면 ThumbnailSweeper 도 못 찾아 R2 에 영영 남는다.
    @Test
    void 지워진_미디어면_방금_올린_썸네일을_지운다() {
        StoredFile file = processing();
        StorageKey thumbnailKey = file.getStorageKey().thumbnail();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        mediaFinisher.finish(file.getId(), processed(file));

        verify(fileStoragePort).delete(thumbnailKey);
    }

    @Test
    void 지워진_미디어는_실패_처리로도_되살아나지_않는다() {
        StoredFile file = processing();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));
        int before = fileRepository.findAllByRoomId(ROOM_ID).size();

        mediaFinisher.markFailed(file.getId());

        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).hasSize(before);
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
        return new ProcessedMedia(file.getStorageKey().thumbnail(), 1200, 900, null, null);
    }
}
