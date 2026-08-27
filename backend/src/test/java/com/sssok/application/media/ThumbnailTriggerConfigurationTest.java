package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

class ThumbnailTriggerConfigurationTest {

    @Test
    void 스프링부트가_제공하는_비동기_실행기를_사용한다() throws NoSuchMethodException {
        Method listener = ThumbnailTrigger.class.getMethod("onMediaCreated", MediaCreatedEvent.class);

        assertThat(listener.getAnnotation(Async.class).value())
            .isEqualTo("applicationTaskExecutor");
    }
}
