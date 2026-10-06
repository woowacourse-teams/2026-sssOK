package com.sssok.infrastructure.config;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.search.AnalyzeImageService;
import com.sssok.application.search.ImageAnalysisDispatcher;
import com.sssok.application.search.ImageAnalysisRegistrar;
import com.sssok.application.search.ImageAnalysisTrigger;
import com.sssok.infrastructure.scheduler.ImageAnalysisSweeper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(name = "media.search.enabled", havingValue = "true")
@EnableConfigurationProperties(ImageSearchProperties.class)
public class ImageSearchWorkerConfig {
    @Bean(name = "imageAnalysisTaskExecutor")
    public AsyncTaskExecutor executor(ImageSearchProperties properties) {
        if (properties.createdSince() == null) {
            throw new IllegalArgumentException("media.search.created-since에 기능 도입 시각을 지정해주세요");
        }
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("image-analysis-");
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        return executor;
    }

    @Bean
    public AnalyzeImageService analyzer(MediaSearchDocumentRepository documents, FileRepository files,
        FileStoragePort storage, ImageDescriptionPort descriptions, TextEmbeddingPort embeddings,
        ImageSearchProperties properties) {
        return new AnalyzeImageService(documents, files, storage, descriptions, embeddings,
            properties, Clock.systemUTC());
    }

    @Bean
    public ImageAnalysisRegistrar registrar(MediaSearchDocumentRepository documents, FileRepository files,
        ImageSearchProperties properties) {
        return new ImageAnalysisRegistrar(documents, files, properties);
    }

    @Bean
    public ImageAnalysisDispatcher dispatcher(
        @Qualifier("imageAnalysisTaskExecutor") AsyncTaskExecutor executor, AnalyzeImageService analyzer) {
        return new ImageAnalysisDispatcher(executor, analyzer);
    }

    @Bean
    public ImageAnalysisTrigger trigger(ImageAnalysisRegistrar registrar, ImageAnalysisDispatcher dispatcher) {
        return new ImageAnalysisTrigger(registrar, dispatcher);
    }

    @Bean
    public ImageAnalysisSweeper sweeper(MediaSearchDocumentRepository documents,
        ImageAnalysisDispatcher dispatcher, ImageSearchProperties properties) {
        return new ImageAnalysisSweeper(documents, dispatcher, properties, Clock.systemUTC());
    }
}
