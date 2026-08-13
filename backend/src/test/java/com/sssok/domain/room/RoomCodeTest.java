package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomCodeException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoomCodeTest {

    private static final String VALID_CHARS_REGEX = "[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}";

    @RepeatedTest(20)
    void 생성된_코드는_8자리이고_혼동_문자를_포함하지_않는다() {
        RoomCode code = RoomCode.generate(new SecureRandom());

        assertThat(code.value()).matches(VALID_CHARS_REGEX);
    }

    @Test
    void 유효한_형식의_코드는_정상_생성된다() {
        RoomCode code = new RoomCode("23456789");

        assertThat(code.value()).isEqualTo("23456789");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1234567",      // 7자리 (길이 부족)
        "123456789",    // 9자리 (길이 초과)
        "ABCDEFG0",     // 혼동 문자 0 포함
        "ABCDEFG1",     // 혼동 문자 1 포함
        "ABCDEFGI",     // 혼동 문자 I 포함
        "ABCDEFGO",     // 혼동 문자 O 포함
        "abcdefgh",     // 소문자 불허
    })
    void 형식에_맞지_않으면_예외(String invalidValue) {
        assertThatThrownBy(() -> new RoomCode(invalidValue))
            .isInstanceOf(InvalidRoomCodeException.class);
    }

    @Test
    void null_값이면_예외() {
        assertThatThrownBy(() -> new RoomCode(null))
            .isInstanceOf(InvalidRoomCodeException.class);
    }
}
