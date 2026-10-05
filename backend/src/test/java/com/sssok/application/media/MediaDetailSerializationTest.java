package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

// MediaDetail 은 media.created·media.ready 의 SSE payload 로 그대로 직렬화된다.
// 방 전체에 한 번 뿌리는 payload 라 보는 사람마다 다른 likedByMe 가 실리면 안 된다.
@JsonTest
class MediaDetailSerializationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void SSE_payload에는_likeCount만_싣고_likedByMe는_싣지_않는다() throws Exception {
        MediaDetail media = new MediaDetail(1L, "IMAGE", "사진.jpg", "image/jpeg", 1024L,
            null, null, null, null, null, null, null,
            List.of(), 7L, "가현", "READY", Instant.now(), 3, true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(media));

        assertThat(json.get("likeCount").asLong()).isEqualTo(3);
        assertThat(json.has("likedByMe")).isFalse();
    }
}
