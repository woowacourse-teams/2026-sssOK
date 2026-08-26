package com.sssok.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    private static final Set<Integer> ALLOWED_STATUSES = Set.of(400, 401, 403, 404, 405, 409, 410, 413, 415, 500);

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void 모든_코드는_HttpStatus로_변환된다(ErrorCode errorCode) {
        // 표현 계층이 HttpStatus.valueOf 로 바꾸므로, 없는 상태 코드를 적으면 응답을 만들다 터진다.
        assertThatCode(() -> HttpStatus.valueOf(errorCode.status())).doesNotThrowAnyException();
        assertThat(errorCode.status()).isIn(ALLOWED_STATUSES);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void 모든_코드는_메시지를_갖는다(ErrorCode errorCode) {
        assertThat(errorCode.message()).isNotBlank();
    }

    @Test
    void 자리표시자가_있으면_인자로_채운다() {
        assertThat(ErrorCode.ROOM_NOT_FOUND.message(7L)).isEqualTo("존재하지 않는 방입니다: 7");
    }

    @Test
    void 인자를_주지_않으면_형식_문자열을_그대로_돌려준다() {
        // 인자 없이 String.format 을 태우면 자리표시자가 있는 메시지에서 예외가 난다.
        assertThat(ErrorCode.ROOM_NOT_FOUND.message()).isEqualTo("존재하지 않는 방입니다: %s");
    }
}