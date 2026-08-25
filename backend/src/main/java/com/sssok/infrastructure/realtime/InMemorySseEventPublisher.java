package com.sssok.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// EventPublisherPort의 인메모리 SSE 구현체.
// 구독 중인 SseEmitter를 이 JVM의 힙 메모리(Map)에만 들고 있으므로 단일 인스턴스 전제다
// (스케일 아웃 시 Redis Pub/Sub 등으로 교체 필요 — #47 PurgeBatch와 같은 전제).
@Component
@RequiredArgsConstructor
public class InMemorySseEventPublisher implements EventPublisherPort, EventSubscriberPort {

    private final RoomEventJpaRepository roomEventJpaRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, List<SseEmitter>> emittersByRoom = new ConcurrentHashMap<>();

    // room_events 저장 + 브로드캐스트(publish)와, 구독 등록 + replay 조회(subscribe)가
    // 같은 방에 대해 겹치면 같은 이벤트가 두 번 전달되거나 순서가 뒤바뀔 수 있다.
    // 그래서 이 둘을 방 단위로(같은 emitters 리스트 객체에 synchronized) 직렬화해서,
    // "구독 시점 이후에 생긴 이벤트"는 항상 live broadcast로만, "구독 이전 이벤트"는 항상 replay로만 오게 한다.
    @Override
    public void publish(Long roomId, String eventType, Object payload) {
        String json = serialize(payload);
        List<SseEmitter> emitters = emittersFor(roomId);
        synchronized (emitters) {
            RoomEventJpaEntity saved = roomEventJpaRepository.save(
                new RoomEventJpaEntity(roomId, eventType, json, Instant.now()));
            broadcast(emitters, roomId, saved.getId(), eventType, json);
        }
    }

    // 컨트롤러가 구독을 등록할 때 호출한다. lastEventId가 있으면 놓친 이벤트부터 먼저 재전송한다.
    @Override
    public SseEmitter subscribe(Long roomId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter();
        List<SseEmitter> emitters = emittersFor(roomId);
        synchronized (emitters) {
            emitters.add(emitter);

            emitter.onCompletion(() -> remove(roomId, emitter));
            emitter.onTimeout(() -> remove(roomId, emitter));
            emitter.onError(e -> remove(roomId, emitter));

            if (lastEventId != null) {
                replayMissedEvents(roomId, lastEventId, emitter);
            }
        }
        return emitter;
    }

    private List<SseEmitter> emittersFor(Long roomId) {
        return emittersByRoom.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>());
    }

    // 테스트 전용: 열어둔 SSE 스트림은 타임아웃이 없어(-1) 저절로 끝나지 않으므로,
    // 테스트가 남겨둔 emitter를 명시적으로 완료 처리해 다음 테스트로 새지 않게 한다.
    public void completeAll(Long roomId) {
        List<SseEmitter> emitters = emittersByRoom.getOrDefault(roomId, List.of());
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            emitter.complete();
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void sendHeartbeat() {
        emittersByRoom.forEach((roomId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    remove(roomId, emitter);
                }
            }
        });
    }

    private void replayMissedEvents(Long roomId, Long lastEventId, SseEmitter emitter) {
        List<RoomEventJpaEntity> missed =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, lastEventId);
        for (RoomEventJpaEntity event : missed) {
            sendTo(emitter, roomId, event.getId(), event.getEventType(), event.getPayload());
        }
    }

    private void broadcast(List<SseEmitter> emitters, Long roomId, Long eventId, String eventType, String json) {
        for (SseEmitter emitter : emitters) {
            sendTo(emitter, roomId, eventId, eventType, json);
        }
    }

    private void sendTo(SseEmitter emitter, Long roomId, Long eventId, String eventType, String json) {
        try {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(eventId))
                .name(eventType)
                .data(json));
        } catch (IOException e) {
            remove(roomId, emitter);
        }
    }

    private void remove(Long roomId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByRoom.get(roomId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    // 직렬화에 실패하면 "{}" 같은 임시값으로 얼버무리지 않고 여기서 바로 중단시킨다.
    // publish()가 이 예외로 실패하면 room_events 저장·브로드캐스트 모두 일어나지 않는다.
    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE payload 직렬화 실패: " + payload, e);
        }
    }
}
