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
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class MediaDeleterTest {

    private static final StorageKey ORIGINAL = new StorageKey("rooms/10/1.jpg");
    private static final StorageKey THUMBNAIL = new StorageKey("rooms/10/thumbnails/1.jpg");

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
    private final OrphanObjectCollector orphanObjectCollector = mock(OrphanObjectCollector.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final MediaDeleter mediaDeleter = new MediaDeleter(
        fileRepository, folderMediaRepository, orphanObjectCollector, eventPublisher);

    @Test
    void 정리_대상을_남긴_뒤_관계와_행을_삭제하고_이벤트를_발행한다() {
        mediaDeleter.delete(10L, List.of(fileWithThumbnail()));

        InOrder order = inOrder(
            orphanObjectCollector, folderMediaRepository, fileRepository, eventPublisher);
        order.verify(orphanObjectCollector).enqueue(List.of(ORIGINAL, THUMBNAIL));
        order.verify(folderMediaRepository).detachFromAllFolders(List.of(1L));
        order.verify(fileRepository).deleteAllByIdIn(List.of(1L));
        order.verify(eventPublisher).publishEvent(new MediaDeletedEvent(10L, List.of(1L)));
        order.verify(eventPublisher)
            .publishEvent(new ObjectsOrphanedEvent(List.of(ORIGINAL, THUMBNAIL)));
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
        assertThat(captor.getValue()).hasSize(1000);
    }

    // 삭제 트랜잭션이 읽은 thumbnailKey 가 null 이어도, 워커가 그 사이 썸네일을 올렸을 수 있다.
    // 키는 원본에서 유도되므로 늘 정리 대상에 넣는다 (#255).
    @Test
    void 썸네일_키가_비어_있어도_유도한_키를_정리_대상에_넣는다() {
        StoredFile file = mock(StoredFile.class);
        when(file.getId()).thenReturn(1L);
        when(file.getStorageKey()).thenReturn(ORIGINAL);
        when(file.getThumbnailKey()).thenReturn(null);

        mediaDeleter.delete(10L, List.of(file));

        verify(orphanObjectCollector).enqueue(List.of(ORIGINAL, THUMBNAIL));
    }

    private StoredFile fileWithThumbnail() {
        return fileAt(1L);
    }

    private static StoredFile fileAt(long id) {
        StoredFile file = mock(StoredFile.class);
        when(file.getId()).thenReturn(id);
        when(file.getStorageKey()).thenReturn(new StorageKey("rooms/10/%d.jpg".formatted(id)));
        return file;
    }
}
