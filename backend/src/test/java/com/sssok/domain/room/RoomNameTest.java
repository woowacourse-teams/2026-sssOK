package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomNameException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomNameTest {

    @Test
    void 정상적인_이름은_생성된다() {
        RoomName name = new RoomName("우테코 회식");

        assertThat(name.value()).isEqualTo("우테코 회식");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void 빈_문자열이거나_공백뿐이면_예외(String invalidValue) {
        assertThatThrownBy(() -> new RoomName(invalidValue))
            .isInstanceOf(InvalidRoomNameException.class);
    }

    @Test
    void 최대_길이를_넘으면_예외() {
        String tooLong = "가".repeat(31);

        assertThatThrownBy(() -> new RoomName(tooLong))
            .isInstanceOf(InvalidRoomNameException.class);
    }

    @Test
    void 최대_길이까지는_허용된다() {
        String exactly30 = "가".repeat(30);

        assertThat(new RoomName(exactly30).value()).hasSize(30);
    }
}
