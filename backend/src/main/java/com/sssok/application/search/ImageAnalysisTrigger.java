package com.sssok.application.search;

import com.sssok.application.media.MediaReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
public class ImageAnalysisTrigger {
    private final ImageAnalysisRegistrar registrar;
    private final ImageAnalysisDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(MediaReadyEvent event) {
        try {
            registrar.register(event.mediaId()); // 별도 트랜잭션 커밋 후 실행기에 제출한다.
            dispatcher.submit(event.mediaId());
        } catch (RuntimeException exception) {
            // 원본 업로드는 이미 완료되었다. 누락된 등록은 배치가 다시 찾는다.
            log.warn("이미지 분석 등록 실패. 복구 배치가 재시도합니다. mediaId={}", event.mediaId());
        }
    }
}
