package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

// 영상과 사진이 같은 풀을 쓰면 ffmpeg 한 무더기에 ZIP 압축과 사진 썸네일까지 함께 멈춘다.
// 풀이 갈려 있는지, 그리고 풀이 가득 찼을 때 등록 흐름이 깨지지 않는지 확인한다.
class ThumbnailTriggerConfigurationTest {

    private static final Long MEDIA_ID = 77L;

    private final GenerateThumbnailService generateThumbnailService =
        mock(GenerateThumbnailService.class);
    private final AsyncTaskExecutor imageTaskExecutor = mock(AsyncTaskExecutor.class);
    private final AsyncTaskExecutor videoTaskExecutor = mock(AsyncTaskExecutor.class);
    private final ThumbnailTrigger trigger = new ThumbnailTrigger(
        generateThumbnailService, imageTaskExecutor, videoTaskExecutor);

    @Test
    void 사진은_공용_비동기_실행기를_사용한다() {
        trigger.onImageCreated(event("IMAGE"));

        then(imageTaskExecutor).should().execute(any(Runnable.class));
        then(videoTaskExecutor).should(never()).execute(any(Runnable.class));
    }

    @Test
    void 영상은_전용_비동기_실행기를_사용한다() {
        trigger.onVideoCreated(event("VIDEO"));

        then(videoTaskExecutor).should().execute(any(Runnable.class));
        then(imageTaskExecutor).should(never()).execute(any(Runnable.class));
    }

    @Test
    void 제출한_작업은_썸네일_워커를_부른다() {
        trigger.onVideoCreated(event("VIDEO"));

        submittedTask(videoTaskExecutor).run();

        then(generateThumbnailService).should().generate(MEDIA_ID);
    }

    // 거부가 그대로 튀어나오면 등록은 이미 커밋된 채 사용자만 오류를 본다.
    @Test
    void 워커_큐가_가득_차도_등록_흐름을_깨뜨리지_않는다() {
        willThrow(new TaskRejectedException("대기열이 가득 찼습니다"))
            .given(videoTaskExecutor).execute(any(Runnable.class));

        assertThatCode(() -> trigger.onVideoCreated(event("VIDEO")))
            .doesNotThrowAnyException();
    }

    private Runnable submittedTask(AsyncTaskExecutor taskExecutor) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(task.capture());
        return task.getValue();
    }

    private MediaCreatedEvent event(String type) {
        return new MediaCreatedEvent(1L, new MediaDetail(
            MEDIA_ID, type, "미디어", "video/mp4", 1024L,
            null, null, null, null, null, null, null,
            List.of(), 1L, "업로더", "PROCESSING", Instant.now()));
    }
}
