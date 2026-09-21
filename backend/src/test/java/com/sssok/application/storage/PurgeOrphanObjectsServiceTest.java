package com.sssok.application.storage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StorageKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurgeOrphanObjectsServiceTest {

    private static final StorageKey FIRST = new StorageKey("rooms/10/a.jpg");
    private static final StorageKey SECOND = new StorageKey("rooms/10/b.jpg");

    private final FileStoragePort fileStoragePort = mock(FileStoragePort.class);
    private final OrphanObjectCollector collector = mock(OrphanObjectCollector.class);
    private final PurgeOrphanObjectsService service =
        new PurgeOrphanObjectsService(fileStoragePort, collector);

    @Test
    void 키를_한_번에_넘기고_지워진_것을_대기열에서_뺀다() {
        when(fileStoragePort.deleteAll(List.of(FIRST, SECOND))).thenReturn(List.of());

        service.purge(List.of(FIRST, SECOND));

        verify(fileStoragePort).deleteAll(List.of(FIRST, SECOND));
        verify(collector).markDone(List.of(FIRST, SECOND));
    }

    // 지우지 못한 키의 행을 함께 지우면 다음 회차가 그 오브젝트를 영영 찾지 못한다.
    @Test
    void 지우지_못한_키는_대기열에_남긴다() {
        when(fileStoragePort.deleteAll(List.of(FIRST, SECOND))).thenReturn(List.of(SECOND));

        service.purge(List.of(FIRST, SECOND));

        verify(collector).markDone(List.of(FIRST));
    }

    // 여기서 예외가 올라가 봐야 받을 곳이 없다 — @Async 스레드이거나 스케줄 배치다.
    // 대기열을 건드리지 않고 넘어가야 회수 배치가 다시 태운다.
    @Test
    void 스토리지가_통째로_실패해도_예외를_퍼뜨리지_않고_대기열을_유지한다() {
        when(fileStoragePort.deleteAll(List.of(FIRST)))
            .thenThrow(new IllegalStateException("R2 down"));

        service.purge(List.of(FIRST));

        verifyNoInteractions(collector);
    }

    @Test
    void 지울_것이_없으면_스토리지를_부르지_않는다() {
        service.purge(List.of());

        verifyNoInteractions(fileStoragePort);
        verifyNoInteractions(collector);
    }
}
