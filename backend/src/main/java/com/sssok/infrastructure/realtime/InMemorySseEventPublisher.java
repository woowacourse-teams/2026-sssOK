package com.sssok.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.domain.room.RoomMember;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// EventPublisherPort의 인메모리 SSE 구현체.
// 구독 중인 SseEmitter를 이 JVM의 힙 메모리(Map)에만 들고 있으므로 단일 인스턴스 전제다
// (스케일 아웃 시 Redis Pub/Sub 등으로 교체 필요 — 스케줄 배치들과 같은 전제).
@Component
@RequiredArgsConstructor
public class InMemorySseEventPublisher implements EventPublisherPort, EventSubscriberPort {

    private final RoomEventJpaRepository roomEventJpaRepository;
    private final ObjectMapper objectMapper;
    private final RoomMemberRepository roomMemberRepository;

    private final Map<Long, List<SseEmitter>> emittersByRoom = new ConcurrentHashMap<>();
    private final Map<SseEmitter, Long> membershipIdByEmitter = new ConcurrentHashMap<>();
    // 회원 전용 이벤트를 그 회원의 연결에만 보내기 위해 연결마다 회원 ID 를 기억한다.
    private final Map<SseEmitter, Long> memberIdByEmitter = new ConcurrentHashMap<>();

    // room_events 저장 + 브로드캐스트(publish)와, 구독 등록 + replay 조회(subscribe)가
    // 같은 방에 대해 겹치면 같은 이벤트가 두 번 전달되거나 순서가 뒤바뀔 수 있다.
    // 그래서 이 둘을 방 단위로(같은 emitters 리스트 객체에 synchronized) 직렬화해서,
    // "구독 시점 이후에 생긴 이벤트"는 항상 live broadcast로만, "구독 이전 이벤트"는 항상 replay로만 오게 한다.
    @Override
    public void publish(Long roomId, String eventType, Object payload, Long recipientMemberId) {
        String json = serialize(payload);
        List<SseEmitter> emitters = getOrCreateEmitters(roomId);
        synchronized (emitters) {
            RoomEventJpaEntity saved = roomEventJpaRepository.save(
                new RoomEventJpaEntity(roomId, eventType, json, Instant.now(), recipientMemberId));
            broadcast(emitters, roomId, saved.getId(), eventType, json, recipientMemberId);
        }
    }

    // 컨트롤러가 구독을 등록할 때 호출한다. lastEventId가 있으면 놓친 이벤트부터 먼저 재전송한다.
    @Override
    public SseEmitter subscribe(Long roomId, Long memberId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter();
        List<SseEmitter> emitters = getOrCreateEmitters(roomId);
        synchronized (emitters) {
            // 나가기의 연결 종료와 같은 모니터 안에서 검증·등록해 뒤늦게 구독이 남지 않게 한다.
            RoomMember membership = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
                .orElseThrow(RoomMembershipRequiredException::new);
            membershipIdByEmitter.put(emitter, membership.getId());
            memberIdByEmitter.put(emitter, memberId);
            emitters.add(emitter);

            emitter.onCompletion(() -> removeEmitter(roomId, emitter));
            emitter.onTimeout(() -> removeEmitter(roomId, emitter));
            emitter.onError(e -> removeEmitter(roomId, emitter));

            if (lastEventId != null) {
                replayMissedEvents(roomId, lastEventId, memberId, emitter);
            }
        }
        return emitter;
    }

    @Override
    public void disconnectMembership(Long roomId, Long membershipId) {
        List<SseEmitter> emitters = getOrCreateEmitters(roomId);
        synchronized (emitters) {
            for (SseEmitter emitter : new ArrayList<>(emitters)) {
                if (membershipId.equals(membershipIdByEmitter.get(emitter))) {
                    completeEmitter(roomId, emitter);
                }
            }
        }
    }

    private List<SseEmitter> getOrCreateEmitters(Long roomId) {
        return emittersByRoom.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>());
    }

    // 타임아웃 없는(-1) 스트림은 저절로 끝나지 않아 종료·테스트 정리 시 직접 닫는다.
    public void completeAll(Long roomId) {
        List<SseEmitter> emitters = emittersByRoom.getOrDefault(roomId, List.of());
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            completeEmitter(roomId, emitter);
        }
    }

    // 브라우저는 Last-Event-ID 로 재구독해 놓친 이벤트를 replay 로 받는다.
    @EventListener(ContextClosedEvent.class)
    public void completeAllOnShutdown() {
        emittersByRoom.keySet().forEach(this::completeAll);
    }

    @Scheduled(fixedRate = 15_000)
    public void sendHeartbeat() {
        emittersByRoom.forEach((roomId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(roomId, emitter);
                }
            }
        });
    }

    private void replayMissedEvents(Long roomId, Long lastEventId, Long memberId, SseEmitter emitter) {
        List<RoomEventJpaEntity> missed = roomEventJpaRepository.findVisibleAfter(roomId, lastEventId, memberId);
        for (RoomEventJpaEntity event : missed) {
            sendTo(emitter, roomId, event.getId(), event.getEventType(), event.getPayload());
        }
    }

    private void broadcast(List<SseEmitter> emitters, Long roomId, Long eventId, String eventType, String json,
                           Long recipientMemberId) {
        for (SseEmitter emitter : emitters) {
            if (recipientMemberId == null || recipientMemberId.equals(memberIdByEmitter.get(emitter))) {
                sendTo(emitter, roomId, eventId, eventType, json);
            }
        }
    }

    private void sendTo(SseEmitter emitter, Long roomId, Long eventId, String eventType, String json) {
        try {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(eventId))
                .name(eventType)
                .data(json));
        } catch (IOException | IllegalStateException e) {
            removeEmitter(roomId, emitter);
        }
    }

    private void completeEmitter(Long roomId, SseEmitter emitter) {
        removeEmitter(roomId, emitter);
        emitter.complete();
    }

    private void removeEmitter(Long roomId, SseEmitter emitter) {
        membershipIdByEmitter.remove(emitter);
        memberIdByEmitter.remove(emitter);
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
