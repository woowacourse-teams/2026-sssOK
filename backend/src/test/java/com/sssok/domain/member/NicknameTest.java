package com.sssok.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.member.exception.InvalidNicknameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameTest {

    @Test
    void 유효한_닉네임은_정상_생성된다() {
        Nickname nickname = new Nickname("로지");
        assertThat(nickname.value()).isEqualTo("로지");
    }

    @Test
    void 앞뒤_공백은_제거된다() {
        Nickname nickname = new Nickname("  로지  ");

        assertThat(nickname.value()).isEqualTo("로지");
    }

    @Test
    void 최대_길이인_12자는_허용된다() {
        String twelveChars = "가".repeat(12);

        assertThat(new Nickname(twelveChars).value()).hasSize(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
    })
    void 비어있으면_예외(String invalidValue) {
        assertThatThrownBy(() -> new Nickname(invalidValue))
            .isInstanceOf(InvalidNicknameException.class);
    }

    @Test
    void 길이_제한을_넘으면_예외() {
        String thirteenChars = "가".repeat(13);

        assertThatThrownBy(() -> new Nickname(thirteenChars))
            .isInstanceOf(InvalidNicknameException.class);
    }

    @Test
    void null_이면_예외() {
        assertThatThrownBy(() -> new Nickname(null))
            .isInstanceOf(InvalidNicknameException.class);
    }
}
