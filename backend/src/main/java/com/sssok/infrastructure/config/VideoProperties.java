package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 영상 썸네일 추출에 쓰는 값. 실행 스레드는 spring.task.execution 의 공용 워커 풀을 쓴다.
@ConfigurationProperties(prefix = "media.video")
public record VideoProperties(
    // 어느 지점의 프레임을 쓸지. 맨 첫 프레임이 검은 화면인 영상이 많아 조금 뒤를 집는다.
    Duration frameAt,
    // 이 시간을 넘기면 포기하고 썸네일 없이 완료한다. 추출기를 죽이지 않으면 좀비가 쌓인다.
    Duration timeout,
    // 목록 타일 기준 너비. 이미지 썸네일과 같은 값으로 둬야 한 줄에 섞여도 크기가 맞는다.
    Integer thumbnailMaxWidth,
    // 추출기에 넘길 원본 서명 URL 의 유효기간. timeout 보다 넉넉해야 추출 도중에 만료되지 않는다.
    Duration sourceUrlTtl
) {

    public VideoProperties {
        frameAt = frameAt == null ? Duration.ofSeconds(1) : frameAt;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        thumbnailMaxWidth = thumbnailMaxWidth == null ? 400 : thumbnailMaxWidth;
        sourceUrlTtl = sourceUrlTtl == null ? Duration.ofMinutes(10) : sourceUrlTtl;
    }
}