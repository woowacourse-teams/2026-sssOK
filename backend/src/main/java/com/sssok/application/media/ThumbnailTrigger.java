package com.sssok.application.media;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 등록이 커밋된 뒤에 썸네일 작업을 띄운다.
//
// AFTER_COMMIT 인 이유: 커밋 전에 시작하면 워커가 아직 없는 행을 찾아 아무 일도 하지 않고 끝난다.
// @Async 인 이유: 원본을 내려받아 줄여서 다시 올리는 데 수 초가 걸린다. 응답이 그동안 기다리면
// 30장을 올린 사용자는 화면이 멈춘 것으로 본다.
//
// 끌 수 있게 해둔 이유: 협력 객체를 목으로 둔 테스트에서 이 스레드가 같은 목을 동시에 건드리면
// 검증이 간헐적으로 깨진다. 워커 자체는 GenerateThumbnailService 를 직접 불러 검증한다.
@Component
@ConditionalOnProperty(name = "media.thumbnail.auto-generate", havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class ThumbnailTrigger {

    private final GenerateThumbnailService generateThumbnailService;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
        condition = "#event.media().type() == 'IMAGE'")
    public void onImageCreated(MediaCreatedEvent event) {
        generateThumbnailService.generate(event.media().mediaId());
    }

    // ffmpeg는 파일 하나에도 수십 초가 걸릴 수 있다. ZIP 압축·사진 썸네일과 같은 풀을 쓰면
    // 영상 몇 개만으로 공용 풀이 차므로, 영상만 별도 풀에서 처리한다.
    @Async("videoThumbnailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
        condition = "#event.media().type() == 'VIDEO'")
    public void onVideoCreated(MediaCreatedEvent event) {
        generateThumbnailService.generate(event.media().mediaId());
    }
}
