package com.sssok.renewal.outgoing.processing.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImageProcessorConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(com.sssok.renewal.outgoing.monitoring.UploadTracing.class,
            () -> org.mockito.Mockito.mock(com.sssok.renewal.outgoing.monitoring.UploadTracing.class))
        .withUserConfiguration(ImageDerivativeOperations.class, LibvipsImageProcessor.class,
            ThumbnailatorImageProcessor.class);

    @org.junit.jupiter.api.Tag("native-image")
    @Test
    void JNI_설정으로_엔진을_구성한다() {
        context.withPropertyValues("media.image.processor=libvips").run(app -> {
            assertThat(app).hasNotFailed().hasSingleBean(ImageDerivativeEngine.class);
            assertThat(app.getBean(ImageDerivativeEngine.class)).isInstanceOf(LibvipsImageProcessor.class);
        });
    }

    @Test
    void Java_복귀_설정은_JNI를_띄우지_않는다() {
        context.withPropertyValues("media.image.processor=java").run(app -> {
            assertThat(app).hasNotFailed().doesNotHaveBean(ImageDerivativeEngine.class)
                .hasSingleBean(ThumbnailatorImageProcessor.class);
        });
    }
}
