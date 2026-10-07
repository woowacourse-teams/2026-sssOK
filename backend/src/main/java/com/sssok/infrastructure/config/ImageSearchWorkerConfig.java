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
    @Bean(name = "imageAnalysisClock")
    public Clock imageAnalysisClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "imageAnalysisTaskExecutor")
    public AsyncTaskExecutor imageAnalysisTaskExecutor(ImageSearchProperties properties) {
        if (properties.createdSince() == null) {
            throw new IllegalArgumentException("media.search.created-since에 기능 도입 시각을 지정해주세요");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("image-analysis-");
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        return executor;
    }

    @Bean
    public AnalyzeImageService analyzeImageService(MediaSearchDocumentRepository documents, FileRepository files,
        FileStoragePort storage, ImageDescriptionPort descriptions, TextEmbeddingPort embeddings,
        ImageSearchProperties properties, @Qualifier("imageAnalysisClock") Clock clock) {
        return new AnalyzeImageService(documents, files, storage, descriptions, embeddings,
            properties, clock);
    }

    @Bean
    public ImageAnalysisRegistrar imageAnalysisRegistrar(MediaSearchDocumentRepository documents, FileRepository files,
        ImageSearchProperties properties, @Qualifier("imageAnalysisClock") Clock clock) {
        return new ImageAnalysisRegistrar(documents, files, properties, clock);
    }

    @Bean
    public ImageAnalysisDispatcher imageAnalysisDispatcher(
        @Qualifier("imageAnalysisTaskExecutor") AsyncTaskExecutor executor, AnalyzeImageService analyzer) {
        return new ImageAnalysisDispatcher(executor, analyzer);
    }

    @Bean
    public ImageAnalysisTrigger imageAnalysisTrigger(ImageAnalysisRegistrar registrar, ImageAnalysisDispatcher dispatcher) {
        return new ImageAnalysisTrigger(registrar, dispatcher);
    }

    @Bean
    public ImageAnalysisSweeper imageAnalysisSweeper(MediaSearchDocumentRepository documents,
        ImageAnalysisDispatcher dispatcher, ImageSearchProperties properties, @Qualifier("imageAnalysisClock") Clock clock) {
        return new ImageAnalysisSweeper(documents, dispatcher, properties, clock);
    }
}
