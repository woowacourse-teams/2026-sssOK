package com.sssok.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.EventPublisherPort;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// EventPublisherPort의 인메모리 SSE 구현체.
// 구독 중인 SseEmitter를 이 JVM의 힙 메모리(Map)에만 들고 있으므로 단일 인스턴스 전제다
// (스케일 아웃 시 Redis Pub/Sub 등으로 교체 필요 — #47 PurgeBatch와 같은 전제).
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemorySseEventPublisher implements EventPublisherPort {

    private final RoomEventJpaRepository roomEventJpaRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, List<SseEmitter>> emittersByRoom = new ConcurrentHashMap<>();

    @Override
    public void publish(Long roomId, String eventType, Object payload) {
        String json = serialize(payload);
        RoomEventJpaEntity saved = roomEventJpaRepository.save(
            new RoomEventJpaEntity(roomId, eventType, json, Instant.now()));
        broadcast(roomId, saved.getId(), eventType, json);
    }

    // 컨트롤러가 구독을 등록할 때 호출한다. lastEventId가 있으면 놓친 이벤트부터 먼저 재전송한다.
    public SseEmitter subscribe(Long roomId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter();
        List<SseEmitter> emitters = emittersByRoom.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> remove(roomId, emitter));
        emitter.onTimeout(() -> remove(roomId, emitter));
        emitter.onError(e -> remove(roomId, emitter));

        if (lastEventId != null) {
            replayMissedEvents(roomId, lastEventId, emitter);
        }
        return emitter;
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

    private void broadcast(Long roomId, Long eventId, String eventType, String json) {
        List<SseEmitter> emitters = emittersByRoom.getOrDefault(roomId, List.of());
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

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("SSE payload 직렬화 실패", e);
            return "{}";
        }
    }
}
