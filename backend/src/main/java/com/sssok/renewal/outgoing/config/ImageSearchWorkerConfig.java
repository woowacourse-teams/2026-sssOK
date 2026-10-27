package com.sssok.renewal.outgoing.config;

import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.ai.OpenAiImageDescriptionClient;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository;
import com.sssok.renewal.outgoing.ai.OpenAiTextEmbeddingClient;
import com.sssok.renewal.application.search.AnalyzeImageService;
import com.sssok.renewal.application.search.ImageAnalysisDispatcher;
import com.sssok.renewal.application.search.ImageAnalysisRegistrar;
import com.sssok.renewal.incoming.search.ImageAnalysisTrigger;
import com.sssok.renewal.incoming.search.ImageAnalysisSweeper;
import com.sssok.renewal.incoming.search.ImageAnalysisDispatchScheduler;
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
        // 슬롯 반환 직후 기존 스레드가 종료 처리 중인 경우를 위해 여유를 둔다.
        // 실제 분석 동시성은 Dispatcher의 Semaphore가 제한한다.
        executor.setMaxPoolSize(properties.concurrency() + 1);
        // 대기열은 DB만 사용한다. 슬롯 확보 후 실제 실행한 워커가 선점한다.
        executor.setQueueCapacity(0);
        return executor;
    }

    @Bean
    public AnalyzeImageService analyzeImageService(MediaSearchDocumentRepository documents, UploadFileRepository files,
        FileStorage storage, OpenAiImageDescriptionClient descriptions, OpenAiTextEmbeddingClient embeddings,
        ImageSearchProperties properties, @Qualifier("imageAnalysisClock") Clock clock) {
        return new AnalyzeImageService(documents, files, storage, descriptions, embeddings,
            properties, clock);
    }

    @Bean
    public ImageAnalysisRegistrar imageAnalysisRegistrar(MediaSearchDocumentRepository documents, UploadFileRepository files,
        ImageSearchProperties properties, @Qualifier("imageAnalysisClock") Clock clock) {
        return new ImageAnalysisRegistrar(documents, files, properties, clock);
    }

    @Bean
    public ImageAnalysisDispatcher imageAnalysisDispatcher(
        @Qualifier("imageAnalysisTaskExecutor") AsyncTaskExecutor executor, AnalyzeImageService analyzer,
        MediaSearchDocumentRepository documents, ImageSearchProperties properties,
        @Qualifier("imageAnalysisClock") Clock clock) {
        return new ImageAnalysisDispatcher(executor, analyzer, documents, properties, clock);
    }

    @Bean
    public ImageAnalysisDispatchScheduler imageAnalysisDispatchScheduler(
        ImageAnalysisDispatcher dispatcher) {
        return new ImageAnalysisDispatchScheduler(dispatcher);
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
