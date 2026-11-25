package com.sssok.renewal.outgoing.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

// 방 단위 SSE 발행 구간을 잰다(#833). 발행은 방마다 하나인 모니터 안에서 room_events 저장과 전원 send 를 하므로
// 같은 방 동시 등록이 몰리면 락 앞에 줄이 서고, 구독자가 많거나 느리면 락 보유 시간이 늘어난다.
// 태그는 이벤트 종류·send 종류·결과만 쓴다. roomId 처럼 값이 끝없이 늘어나는 것은 태그로 쓰지 않는다.
@Component
public class SsePublishMetrics {

    public enum SendKind { EVENT, REPLAY, HEARTBEAT }

    private final MeterRegistry registry;

    public SsePublishMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // 구독 연결 수. 발행기가 들고 있는 연결 목록을 그대로 읽는다.
    public void registerConnections(Supplier<Number> connections) {
        Gauge.builder("sssok.sse.connections", connections).register(registry);
    }

    // 모니터에 들어가기까지 기다린 시간. 같은 방 발행·구독 등록이 겹치면 늘어난다.
    public void recordLockWait(String eventType, long nanos) {
        timer("sssok.sse.publish.lock.wait", "event_type", eventType).record(nanos, TimeUnit.NANOSECONDS);
    }

    // 모니터를 쥐고 있던 시간 = room_events 저장 + 전원 send.
    public void recordLockHold(String eventType, long nanos) {
        timer("sssok.sse.publish.lock.hold", "event_type", eventType).record(nanos, TimeUnit.NANOSECONDS);
    }

    // 락 보유 시간 중 room_events INSERT 몫. 나머지가 send 몫이다.
    public void recordSave(long nanos) {
        Timer.builder("sssok.sse.publish.save").register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }

    // publish() 를 부른 시각부터 이 연결에 send 가 끝난 시각까지. 락 대기와 앞 연결들의 send 가 모두 들어간다.
    public void recordDelivery(String eventType, long nanos) {
        timer("sssok.sse.delivery", "event_type", eventType).record(nanos, TimeUnit.NANOSECONDS);
    }

    // 연결 하나에 send 한 번. 읽지 않는 연결은 소켓 버퍼가 차면 여기서 멈춘다.
    public void recordSend(SendKind kind, boolean success, long nanos) {
        String kindTag = kind.name().toLowerCase(Locale.ROOT);
        Counter.builder("sssok.sse.send")
            .tags("kind", kindTag, "outcome", success ? "success" : "failure")
            .register(registry).increment();
        timer("sssok.sse.send.duration", "kind", kindTag).record(nanos, TimeUnit.NANOSECONDS);
    }

    // heartbeat 한 바퀴(전 방·전 연결)에 걸린 시간. 정리 배치와 같은 스케줄러 풀을 쓰므로 길어지면 배치도 밀린다.
    public void recordHeartbeatRound(long nanos) {
        Timer.builder("sssok.sse.heartbeat.round").register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }

    private Timer timer(String name, String tagKey, String tagValue) {
        return Timer.builder(name).tag(tagKey, tagValue).register(registry);
    }
}
