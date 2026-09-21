package com.sssok.application.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 등록이 커밋된 뒤에 썸네일 작업을 띄운다.
//
// AFTER_COMMIT 인 이유: 커밋 전에 시작하면 워커가 아직 없는 행을 찾아 아무 일도 하지 않고 끝난다.
// 다른 스레드로 넘기는 이유: 원본을 내려받아 줄여서 다시 올리는 데 수 초가 걸린다. 응답이 그동안
// 기다리면 30장을 올린 사용자는 화면이 멈춘 것으로 본다.
//
// @Async 대신 실행기를 직접 받는 이유: 풀이 가득 차면 제출이 TaskRejectedException 으로 거부되는데,
// @Async 는 제출이 프록시 안에서 일어나 이 거부를 잡을 자리가 없다. 거부는 실패가 아니라
// "나중에 한다"로 다뤄야 한다 — 행은 PROCESSING 으로 커밋돼 있어 ThumbnailSweeper 가 회수한다.
// StorageCleanupTrigger 와 같은 방식이다.
//
// 끌 수 있게 해둔 이유: 협력 객체를 목으로 둔 테스트에서 이 스레드가 같은 목을 동시에 건드리면
// 검증이 간헐적으로 깨진다. 워커 자체는 GenerateThumbnailService 를 직접 불러 검증한다.
@Slf4j
@Component
@ConditionalOnProperty(name = "media.thumbnail.auto-generate", havingValue = "true",
    matchIfMissing = true)
public class ThumbnailTrigger {

    private final GenerateThumbnailService generateThumbnailService;
    private final AsyncTaskExecutor imageTaskExecutor;
    private final AsyncTaskExecutor videoTaskExecutor;

    // ffmpeg는 파일 하나에도 수십 초가 걸릴 수 있다. ZIP 압축·사진 썸네일과 같은 풀을 쓰면
    // 영상 몇 개만으로 공용 풀이 차므로, 영상만 별도 풀에서 처리한다.
    public ThumbnailTrigger(
        GenerateThumbnailService generateThumbnailService,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor imageTaskExecutor,
        @Qualifier("videoThumbnailTaskExecutor") AsyncTaskExecutor videoTaskExecutor) {
        this.generateThumbnailService = generateThumbnailService;
        this.imageTaskExecutor = imageTaskExecutor;
        this.videoTaskExecutor = videoTaskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
        condition = "#event.media().type() == 'IMAGE'")
    public void onImageCreated(MediaCreatedEvent event) {
        submit(imageTaskExecutor, event.media().mediaId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
        condition = "#event.media().type() == 'VIDEO'")
    public void onVideoCreated(MediaCreatedEvent event) {
        submit(videoTaskExecutor, event.media().mediaId());
    }

    private void submit(AsyncTaskExecutor taskExecutor, Long mediaId) {
        try {
            taskExecutor.execute(() -> generateThumbnailService.generate(mediaId));
        } catch (TaskRejectedException e) {
            // 등록은 이미 커밋됐다. 지금 못 태운 것은 PROCESSING 으로 남아 있으므로 배치가 맡는다.
            log.warn("썸네일 작업을 제출하지 못했습니다. 회수 배치가 다시 처리합니다. mediaId={}", mediaId);
        }
    }
}
