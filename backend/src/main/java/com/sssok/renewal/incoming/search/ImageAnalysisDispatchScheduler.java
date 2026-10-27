package com.sssok.renewal.incoming.search;

import com.sssok.renewal.application.search.ImageAnalysisDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

/** 시작·이벤트 경합·재시도 시각 도래를 보완한다. 장기 실행 복구와 분리한다. */
@RequiredArgsConstructor
public class ImageAnalysisDispatchScheduler {
    private final ImageAnalysisDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${media.search.dispatch-delay:1000}",
        initialDelayString = "${media.search.dispatch-delay:1000}")
    public void dispatch() {
        dispatcher.dispatch();
    }
}
