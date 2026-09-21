package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

class ThumbnailTriggerConfigurationTest {

    @Test
    void 사진은_공용_비동기_실행기를_사용한다() throws NoSuchMethodException {
        Method listener = ThumbnailTrigger.class.getMethod("onImageCreated", MediaCreatedEvent.class);

        assertThat(listener.getAnnotation(Async.class).value())
            .isEqualTo("applicationTaskExecutor");
    }

    @Test
    void 영상은_전용_비동기_실행기를_사용한다() throws NoSuchMethodException {
        Method listener = ThumbnailTrigger.class.getMethod("onVideoCreated", MediaCreatedEvent.class);

        assertThat(listener.getAnnotation(Async.class).value())
            .isEqualTo("videoThumbnailTaskExecutor");
    }
}
