package com.sssok.renewal.application.media.thumbnail;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaEntity;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaRepository;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sssok.support.UploadRoomFixtures;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


// 워커가 도는 사이 사용자가 같은 미디어를 지우거나 폴더에 담는 경합을 확인한다.
@PostgresIntegrationTest
class ThumbnailCompletionEventTest extends PostgresContainerSupport {
    @Autowired
    private JdbcTemplate roomFixtureJdbc;


    private static final Long ROOM_ID = 255L;
    private static final Long UPLOADER_ID = 255L;

    @Autowired
    ThumbnailJobCompletionService completionService;

    @Autowired
    ThumbnailJobJpaRepository jobRepository;

    @MockitoSpyBean
    UploadFileRepository uploadFileRepository;

    private final Map<Long, ThumbnailClaim> claimedJobs = new HashMap<>();

    @Autowired
    FileRepository fileRepository;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    FileStorage fileStorage;

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
            jobRepository.deleteAllById(claimedJobs.keySet());
        });
    }

    @BeforeEach
    void setUpReadUrls() {
        UploadRoomFixtures.ensure(roomFixtureJdbc, 255L);

        Mockito.when(fileStorage.presignGet(
            ArgumentMatchers.any(), ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(), ArgumentMatchers.any()))
            .thenReturn("https://storage.example.com/read");
    }

    @Test
    void 처리가_끝나면_READY로_넘기고_media_ready를_발행한다() {
        StoredFile file = processing();

        completionService.complete(claimedJobs.get(file.getId()), processed(file));

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.READY);
        verify(eventPublisherPort).publish(eq(ROOM_ID), eq("media.ready"), any());
        verify(uploadFileRepository, Mockito.times(1)).findByIdForUpdate(file.getId());
    }

    @Test
    void 지워진_미디어는_되살리지_않고_이벤트도_내보내지_않는다() {
        StoredFile file = processing();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        completionService.complete(claimedJobs.get(file.getId()), processed(file));

        // 되살아난다면 새 id 로 들어오므로 id 가 아니라 방 전체로 확인한다.
        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).isEmpty();
        verify(eventPublisherPort, never()).publish(any(), anyString(), any());
    }

    // 행이 없으면 ThumbnailSweeper 도 못 찾아 R2 에 영영 남는다. 트랜잭션 안에서 직접 지우지 않고
    // 회수 대기열에 남겨 커밋 뒤 정리한다.
    //
    // 썸네일만 넣으면 프리뷰가 스토리지에 남는다. 파생본이 두 종류가 된 뒤로는 둘 다 넣어야 한다.
    @Test
    void 지워진_미디어면_방금_올린_파생본을_모두_정리_대기열에_남긴다() {
        StoredFile file = processing();
        StorageKey thumbnailKey = file.getStorageKey().thumbnail("webp");
        StorageKey previewKey = file.getStorageKey().preview("webp");
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        completionService.complete(claimedJobs.get(file.getId()), processed(file));

        verify(orphanObjectCollector).enqueue(List.of(thumbnailKey, previewKey));
        verify(fileStorage, never()).delete(any());
    }

    // 프리뷰를 만들지 않는 영상·GIF 다. 비어 있는 키를 대기열에 넣으면 정리 배치가 없는 객체를
    // 지우려고 왕복한다.
    @Test
    void 프리뷰가_없으면_썸네일만_대기열에_남긴다() {
        StoredFile file = processing();
        StorageKey thumbnailKey = file.getStorageKey().thumbnail("jpg");
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        completionService.complete(claimedJobs.get(file.getId()),
            ProcessedMedia.ofVideo(thumbnailKey, 1920, 1080, 12, null, null));

        verify(orphanObjectCollector).enqueue(List.of(thumbnailKey));
    }

    // 같은 작업의 늦은 완료 요청이 채택된 파생본을 정리 대상으로 등록하면 안 된다.
    @Test
    void 다른_워커가_먼저_끝냈으면_썸네일을_지우지_않는다() {
        StoredFile file = processing();
        completionService.complete(claimedJobs.get(file.getId()), processed(file));

        completionService.complete(claimedJobs.get(file.getId()), processed(file));

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getThumbnailKey())
            .isEqualTo(file.getStorageKey().thumbnail("webp"));
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void 지워진_미디어는_실패_처리로도_되살아나지_않는다() {
        StoredFile file = processing();
        fileRepository.deleteAllByIdIn(List.of(file.getId()));

        completionService.fail(claimedJobs.get(file.getId()), true, "INVALID_IMAGE");

        assertThat(fileRepository.findAllByRoomId(ROOM_ID)).isEmpty();
    }

    // 워커 트랜잭션이 커밋되기 전에 폴더 담기가 끼어드는 상황.
    // payload 를 미리 만들면 낡은 folderIds 가 실려 나가 방금 담은 폴더가 화면에서 사라진다.
    @Test
    void media_ready_payload는_커밋_시점의_폴더_소속을_담는다() {
        StoredFile file = processing();
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            completionService.complete(claimedJobs.get(file.getId()), processed(file));
            folderMediaRepository.attachToFolder(folder.getId(), List.of(file.getId()));
        });

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisherPort).publish(eq(ROOM_ID), eq("media.ready"), payload.capture());
        assertThat(((MediaDetail) payload.getValue()).folderIds()).containsExactly(folder.getId());
    }

    private StoredFile processing() {
        StoredFile file = StoredFile.reserve(
            ROOM_ID, UPLOADER_ID, "사진.jpg", "image/jpeg", new FileSize(1024), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        StoredFile storedFile = fileRepository.save(file);
        Instant now = Instant.now();
        ThumbnailJob job = new ThumbnailJob(storedFile.getId(), ThumbnailKind.IMAGE, now);
        job.claim(now, now.plusSeconds(120));
        jobRepository.saveAndFlush(new ThumbnailJobJpaEntity(job));
        claimedJobs.put(storedFile.getId(), new ThumbnailClaim(storedFile.getId(), ThumbnailKind.IMAGE,
            job.getClaimToken(), job.getAttempts(), now));
        return storedFile;
    }

    private ProcessedMedia processed(StoredFile file) {
        return ProcessedMedia.ofImage(file.getStorageKey().thumbnail("webp"),
            file.getStorageKey().preview("webp"), 1200, 900, null, null);
    }
}
