package com.sssok.application.port.out;

// 실시간 이벤트 발행 출력
public interface EventPublisherPort {

    // roomId를 구독 중인 클라이언트들에게 eventType/payload를 즉시 전달하고,
    // 재연결 시 재전송(Last-Event-ID)을 위해 기록도 남긴다.
    void publish(Long roomId, String eventType, Object payload);
}
