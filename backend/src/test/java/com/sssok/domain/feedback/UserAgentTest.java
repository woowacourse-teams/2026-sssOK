package com.sssok.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAgentTest {

    @Test
    void 원본_문자열을_그대로_보관한다() {
        String raw = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";

        assertThat(UserAgent.from(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 헤더가_없거나_비어_있으면_알_수_없음으로_둔다(String raw) {
        UserAgent userAgent = UserAgent.from(raw);

        assertThat(userAgent.isUnknown()).isTrue();
        assertThat(userAgent.value()).isNull();
    }

    @Test
    void 최대_길이를_넘으면_잘라서_담는다() {
        UserAgent userAgent = UserAgent.from("A".repeat(UserAgent.MAX_LENGTH + 100));

        assertThat(userAgent.value()).hasSize(UserAgent.MAX_LENGTH);
    }

    @Test
    void 정확히_최대_길이면_자르지_않는다() {
        UserAgent userAgent = UserAgent.from("A".repeat(UserAgent.MAX_LENGTH));

        assertThat(userAgent.value()).hasSize(UserAgent.MAX_LENGTH);
    }
}
