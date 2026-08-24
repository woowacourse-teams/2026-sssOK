package com.sssok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.auth.exception.InvalidLinkCodeException;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkCodeValueTest {

    @Test
    void 항상_6자리_숫자로_생성된다() {
        RandomGenerator fixed = RandomGenerator.of("Random");

        LinkCodeValue code = LinkCodeValue.generate(fixed);

        assertThat(code.value()).matches("\\d{6}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12345a", "", " "})
    void 형식이_아니면_예외(String invalid) {
        assertThatThrownBy(() -> new LinkCodeValue(invalid))
            .isInstanceOf(InvalidLinkCodeException.class);
    }

    @Test
    void null이면_예외() {
        assertThatThrownBy(() -> new LinkCodeValue(null))
            .isInstanceOf(InvalidLinkCodeException.class);
    }
}
