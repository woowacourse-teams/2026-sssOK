package com.sssok.application.port.out;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 실시간 이벤트 구독 출력
public interface EventSubscriberPort {

    // roomId를 구독하는 SseEmitter를 새로 만들어 등록한다.
    // lastEventId가 있으면 재연결로 간주해 그 이후 놓친 이벤트부터 먼저 재전송한다.
    SseEmitter subscribe(Long roomId, Long lastEventId);
}
