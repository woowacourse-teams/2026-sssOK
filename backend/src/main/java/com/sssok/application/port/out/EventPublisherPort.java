package com.sssok.application.port.out;

// 실시간 이벤트 발행 출력
public interface EventPublisherPort {

    // roomId를 구독 중인 클라이언트들에게 eventType/payload를 즉시 전달하고,
    // 재연결 시 재전송(Last-Event-ID)을 위해 기록도 남긴다.
    default void publish(Long roomId, String eventType, Object payload) {
        publish(roomId, eventType, payload, null);
    }

    // recipientMemberId 가 있으면 그 회원에게만 전달한다. 재연결 때 다시 보내는 이벤트도 같은 조건으로 거른다.
    // null 이면 방 전체에 전달한다.
    void publish(Long roomId, String eventType, Object payload, Long recipientMemberId);
}
