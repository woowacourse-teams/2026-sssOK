package com.sssok.infrastructure.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;

// PostgreSQL 은 NUL(U+0000) 을 text 컬럼에 저장하지 못해서, 제어문자가 섞인 값이 저장까지
// 흘러가면 "invalid byte sequence for encoding UTF8: 0x00" 으로 터진다. 값 객체마다 같은
// 검사를 복사하는 대신 본문을 읽는 입구에서 한 번 막는다.
// 탭·개행·복귀는 허용한다 — 여러 줄로 쓰는 의견 본문에서는 정상적인 입력이다.
@JsonComponent
public class ControlCharacterRejectingDeserializer extends StringDeserializer {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = super.deserialize(parser, context);
        if (value != null && hasControlCharacter(value)) {
            // JsonProcessingException 이라야 표현 계층에서 400(INVALID_REQUEST_BODY) 으로 나간다.
            // 평범한 IOException 은 읽기 실패로 취급돼 500 이 된다.
            throw InvalidFormatException.from(
                parser, "문자열에 허용되지 않는 제어문자가 있습니다", value, String.class);
        }
        return value;
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(this::isForbidden);
    }

    private boolean isForbidden(int character) {
        if (character == '\t' || character == '\n' || character == '\r') {
            return false;
        }
        return character < 0x20 || character == 0x7F;
    }
}
