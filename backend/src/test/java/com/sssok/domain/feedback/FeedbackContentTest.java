package com.sssok.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.feedback.exception.FeedbackTooLongException;
import com.sssok.domain.feedback.exception.InvalidFeedbackContentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackContentTest {

    @Test
    void 앞뒤_공백을_제거한다() {
        assertThat(new FeedbackContent("  진행률이 멈춰요  ").value()).isEqualTo("진행률이 멈춰요");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "\t"})
    void 비어_있거나_공백뿐이면_거부한다(String value) {
        assertThatThrownBy(() -> new FeedbackContent(value))
            .isInstanceOf(InvalidFeedbackContentException.class);
    }

    @Test
    void 정확히_1000자는_허용한다() {
        assertThatCode(() -> new FeedbackContent("가".repeat(1000)))
            .doesNotThrowAnyException();
    }

    @Test
    void 최대_길이를_넘으면_거부한다() {
        assertThatThrownBy(() -> new FeedbackContent("가".repeat(1001)))
            .isInstanceOf(FeedbackTooLongException.class);
    }

    @Test
    void 공백을_제거한_뒤의_길이로_판단한다() {
        assertThatCode(() -> new FeedbackContent("  " + "가".repeat(1000) + "  "))
            .doesNotThrowAnyException();
    }
}
