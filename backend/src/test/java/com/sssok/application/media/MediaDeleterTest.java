package com.sssok.application.media;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class MediaDeleterTest {

    @Test
    void 행을_먼저_지운_뒤_원본과_썸네일을_정리하고_이벤트를_발행한다() {
        FileRepository fileRepository = mock(FileRepository.class);
        FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        StoredFile file = mock(StoredFile.class);
        var original = new com.sssok.domain.file.StorageKey("rooms/10/a.jpg");
        var thumbnail = original.thumbnail();
        org.mockito.Mockito.when(file.getId()).thenReturn(1L);
        org.mockito.Mockito.when(file.getStorageKey()).thenReturn(original);

        new MediaDeleter(fileRepository, folderMediaRepository, fileStoragePort, eventPublisher)
            .delete(10L, List.of(file));

        InOrder order = inOrder(fileStoragePort, folderMediaRepository, fileRepository, eventPublisher);
        order.verify(folderMediaRepository).detachFromAllFolders(List.of(1L));
        order.verify(fileRepository).deleteAllByIdIn(List.of(1L));
        order.verify(fileStoragePort).delete(original);
        order.verify(fileStoragePort).delete(thumbnail);
        order.verify(eventPublisher).publishEvent(new MediaDeletedEvent(10L, List.of(1L)));
    }

    // 워커가 방금 올린 썸네일은 삭제 트랜잭션이 읽은 행에 아직 null 로 보인다.
    @Test
    void 썸네일_키가_행에_아직_없어도_유도한_키를_지운다() {
        FileRepository fileRepository = mock(FileRepository.class);
        FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        StoredFile file = mock(StoredFile.class);
        var original = new com.sssok.domain.file.StorageKey("rooms/10/a.jpg");
        org.mockito.Mockito.when(file.getId()).thenReturn(1L);
        org.mockito.Mockito.when(file.getStorageKey()).thenReturn(original);
        org.mockito.Mockito.when(file.getThumbnailKey()).thenReturn(null);

        new MediaDeleter(fileRepository, folderMediaRepository, fileStoragePort, eventPublisher)
            .delete(10L, List.of(file));

        org.mockito.Mockito.verify(fileStoragePort).delete(original.thumbnail());
    }
}
