package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.DerivativeImageProperties;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class MediaDeleterTest {

    private static final StorageKey ORIGINAL = new StorageKey("rooms/10/1.jpg");
    // 파생본 키의 확장자는 원본이 아니라 media.image.format 을 따른다.
    private static final StorageKey THUMBNAIL = new StorageKey("rooms/10/thumbnails/1.webp");
    private static final StorageKey PREVIEW = new StorageKey("rooms/10/previews/1.webp");
    private static final List<StorageKey> ALL_KEYS = List.of(ORIGINAL, THUMBNAIL, PREVIEW);

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
    private final OrphanObjectCollector orphanObjectCollector = mock(OrphanObjectCollector.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final MediaDeleter mediaDeleter = new MediaDeleter(
        fileRepository, folderMediaRepository, orphanObjectCollector, eventPublisher,
        new DerivativeImageProperties(null, null, null));

    @Test
    void 정리_대상을_남긴_뒤_관계와_행을_삭제하고_이벤트를_발행한다() {
        mediaDeleter.delete(10L, List.of(fileWithThumbnail()));

        InOrder order = inOrder(
            orphanObjectCollector, folderMediaRepository, fileRepository, eventPublisher);
        order.verify(orphanObjectCollector).enqueue(ALL_KEYS);
        order.verify(folderMediaRepository).detachFromAllFolders(List.of(1L));
        order.verify(fileRepository).deleteAllByIdIn(List.of(1L));
        order.verify(eventPublisher).publishEvent(new MediaDeletedEvent(10L, List.of(1L)));
        order.verify(eventPublisher)
            .publishEvent(new ObjectsOrphanedEvent(ALL_KEYS));
    }

    // 파일 수만큼 왕복하던 것이 실제로 한 번으로 줄었는지. 트랜잭션 안에서 스토리지를 건드리는
    // 경로 자체가 없어졌다는 것은 생성자에 FileStoragePort 가 없다는 사실이 보장한다.
    @Test
    void 미디어가_몇_장이든_정리_대상을_한_번에_넘긴다() {
        List<StoredFile> files = LongStream.rangeClosed(1, 500)
            .mapToObj(MediaDeleterTest::fileAt)
            .toList();

        mediaDeleter.delete(10L, files);

        ArgumentCaptor<List<StorageKey>> captor = ArgumentCaptor.captor();
        verify(orphanObjectCollector, times(1)).enqueue(captor.capture());
        // 원본 · 썸네일 · 프리뷰 세 벌이다.
        assertThat(captor.getValue()).hasSize(1500);
    }

    // 삭제 트랜잭션이 읽은 파생본 키가 null 이어도, 워커가 그 사이 올렸을 수 있다.
    // 키는 원본에서 유도되므로 늘 정리 대상에 넣는다 (#255).
    @Test
    void 파생본_키가_비어_있어도_유도한_키를_정리_대상에_넣는다() {
        StoredFile file = fileAt(1L);
        when(file.getThumbnailKey()).thenReturn(null);
        when(file.getPreviewKey()).thenReturn(null);

        mediaDeleter.delete(10L, List.of(file));

        verify(orphanObjectCollector).enqueue(ALL_KEYS);
    }

    // 포맷을 바꾸면 지금 설정으로 유도한 키가 예전 파생본과 어긋난다. 행에 저장된 키를 같이
    // 넣지 않으면 그것들이 스토리지에 영영 남는다.
    @Test
    void 설정과_다른_포맷으로_저장된_파생본도_정리_대상에_넣는다() {
        StorageKey legacyThumbnail = new StorageKey("rooms/10/thumbnails/1.jpg");
        StoredFile file = fileAt(1L);
        when(file.getThumbnailKey()).thenReturn(legacyThumbnail);
        when(file.getPreviewKey()).thenReturn(null);

        mediaDeleter.delete(10L, List.of(file));

        verify(orphanObjectCollector)
            .enqueue(List.of(ORIGINAL, legacyThumbnail, THUMBNAIL, PREVIEW));
    }

    // 영상 썸네일은 ffmpeg 이 뽑은 JPEG 프레임이라 파생본 포맷 설정을 따르지 않는다.
    @Test
    void 영상은_JPEG_썸네일_키만_유도하고_프리뷰는_만들지_않는다() {
        StoredFile file = mock(StoredFile.class);
        when(file.getId()).thenReturn(1L);
        when(file.getStorageKey()).thenReturn(new StorageKey("rooms/10/1.mp4"));
        when(file.getMediaType()).thenReturn(MediaType.MP4);
        when(file.needsPreview()).thenReturn(false);

        mediaDeleter.delete(10L, List.of(file));

        verify(orphanObjectCollector).enqueue(List.of(
            new StorageKey("rooms/10/1.mp4"), new StorageKey("rooms/10/thumbnails/1.jpg")));
    }

    private StoredFile fileWithThumbnail() {
        return fileAt(1L);
    }

    private static StoredFile fileAt(long id) {
        StoredFile file = mock(StoredFile.class);
        when(file.getId()).thenReturn(id);
        when(file.getStorageKey()).thenReturn(new StorageKey("rooms/10/%d.jpg".formatted(id)));
        when(file.getMediaType()).thenReturn(MediaType.JPEG);
        when(file.needsPreview()).thenReturn(true);
        return file;
    }
}
