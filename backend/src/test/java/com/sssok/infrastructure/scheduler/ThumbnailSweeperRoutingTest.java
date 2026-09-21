package com.sssok.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.sssok.application.media.GenerateThumbnailService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileRepository.StuckMedia;
import com.sssok.domain.file.MediaType;
import com.sssok.infrastructure.config.ThumbnailProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

// 회수도 최초 등록과 같은 풀에서 돌아야 한다. 배치 한 번에 집는 50개가 전부 영상이고 ffmpeg 가
// 줄줄이 제한 시간을 먹으면, 스케줄 스레드에서 직접 처리할 경우 다른 스케줄 작업까지 밀린다.
class ThumbnailSweeperRoutingTest {

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final GenerateThumbnailService generateThumbnailService =
        mock(GenerateThumbnailService.class);
    private final AsyncTaskExecutor imageTaskExecutor = mock(AsyncTaskExecutor.class);
    private final AsyncTaskExecutor videoTaskExecutor = mock(AsyncTaskExecutor.class);
    private final ThumbnailSweeper sweeper = new ThumbnailSweeper(
        fileRepository, generateThumbnailService,
        new ThumbnailProperties(null, null, null, null),
        imageTaskExecutor, videoTaskExecutor);

    @Test
    void 밀린_영상은_영상_전용_워커에_넘긴다() {
        givenStuck(new StuckMedia(1L, MediaType.MP4));

        sweeper.sweep();

        then(videoTaskExecutor).should().execute(any(Runnable.class));
        then(imageTaskExecutor).should(never()).execute(any(Runnable.class));
    }

    @Test
    void 밀린_사진은_공용_워커에_넘긴다() {
        givenStuck(new StuckMedia(2L, MediaType.JPEG));

        sweeper.sweep();

        then(imageTaskExecutor).should().execute(any(Runnable.class));
        then(videoTaskExecutor).should(never()).execute(any(Runnable.class));
    }

    // 넘기기만 한 시점에는 아직 아무 영상도 처리되지 않아야 한다.
    @Test
    void 스케줄_스레드에서_직접_처리하지_않는다() {
        givenStuck(new StuckMedia(1L, MediaType.MP4), new StuckMedia(2L, MediaType.JPEG));

        sweeper.sweep();

        then(generateThumbnailService).shouldHaveNoInteractions();
    }

    // 거부돼도 행은 PROCESSING 그대로라 다음 배치가 다시 집어 간다.
    @Test
    void 워커_큐가_가득_차도_배치가_멈추지_않는다() {
        givenStuck(new StuckMedia(1L, MediaType.MP4), new StuckMedia(2L, MediaType.JPEG));
        willThrow(new TaskRejectedException("대기열이 가득 찼습니다"))
            .given(videoTaskExecutor).execute(any(Runnable.class));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        then(imageTaskExecutor).should().execute(any(Runnable.class));
    }

    private void givenStuck(StuckMedia... stuck) {
        given(fileRepository.findStuckInProcessing(any(), anyInt())).willReturn(List.of(stuck));
    }
}
