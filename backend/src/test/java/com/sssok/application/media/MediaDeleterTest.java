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
    void 원본과_썸네일을_지운_뒤_관계와_행을_삭제하고_이벤트를_발행한다() {
        FileRepository fileRepository = mock(FileRepository.class);
        FolderMediaRepository folderMediaRepository = mock(FolderMediaRepository.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        StoredFile file = mock(StoredFile.class);
        var original = new com.sssok.domain.file.StorageKey("rooms/10/a.jpg");
        var thumbnail = new com.sssok.domain.file.StorageKey("rooms/10/a_thumb.jpg");
        org.mockito.Mockito.when(file.getId()).thenReturn(1L);
        org.mockito.Mockito.when(file.getStorageKey()).thenReturn(original);
        org.mockito.Mockito.when(file.getThumbnailKey()).thenReturn(thumbnail);

        new MediaDeleter(fileRepository, folderMediaRepository, fileStoragePort, eventPublisher)
            .delete(10L, List.of(file));

        InOrder order = inOrder(fileStoragePort, folderMediaRepository, fileRepository, eventPublisher);
        order.verify(fileStoragePort).delete(original);
        order.verify(fileStoragePort).delete(thumbnail);
        order.verify(folderMediaRepository).detachFromAllFolders(List.of(1L));
        order.verify(fileRepository).deleteAllByIdIn(List.of(1L));
        order.verify(eventPublisher).publishEvent(new MediaDeletedEvent(10L, List.of(1L)));
    }
}
