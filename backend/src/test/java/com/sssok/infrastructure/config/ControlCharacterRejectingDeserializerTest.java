package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 제어문자가 저장까지 흘러가면 PostgreSQL 이 거부해 요청이 500 으로 끝난다.
class ControlCharacterRejectingDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new ControlCharacterRejectingDeserializer());
        objectMapper = new ObjectMapper().registerModule(module);
    }

    private record Payload(String name) {
    }

    @ParameterizedTest(name = "제어문자 {0}")
    @ValueSource(strings = {"\\u0000", "\\u0001", "\\u001f", "\\u007f"})
    void 제어문자가_있으면_읽기에_실패한다(String escaped) {
        String json = "{\"name\":\"a%sb\"}".formatted(escaped);

        assertThatThrownBy(() -> objectMapper.readValue(json, Payload.class))
            .isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest(name = "허용 문자 {0}")
    @ValueSource(strings = {"\\t", "\\n", "\\r"})
    void 탭과_개행은_그대로_읽는다(String escaped) {
        String json = "{\"name\":\"a%sb\"}".formatted(escaped);

        assertThat(readName(json)).hasSize(3);
    }

    @Test
    void 평범한_문자열은_그대로_읽는다() {
        assertThat(readName("{\"name\":\"맛집\"}")).isEqualTo("맛집");
    }

    @Test
    void 값이_null이면_그대로_null이다() {
        assertThat(readName("{\"name\":null}")).isNull();
    }

    private String readName(String json) {
        try {
            return objectMapper.readValue(json, Payload.class).name();
        } catch (IOException e) {
            throw new IllegalStateException("읽을 수 없습니다: " + json, e);
        }
    }
}
