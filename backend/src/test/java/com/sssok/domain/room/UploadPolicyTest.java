package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UploadPolicyTest {

    @Test
    void ANYONE은_누구나_업로드할_수_있다() {
        assertThat(UploadPolicy.ANYONE.allows(true)).isTrue();
        assertThat(UploadPolicy.ANYONE.allows(false)).isTrue();
    }

    @Test
    void HOST_ONLY는_방장만_업로드할_수_있다() {
        assertThat(UploadPolicy.HOST_ONLY.allows(true)).isTrue();
        assertThat(UploadPolicy.HOST_ONLY.allows(false)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "everyone, ANYONE",
        "host, HOST_ONLY",
    })
    void API_문자열을_열거형으로_바꾼다(String apiValue, UploadPolicy expected) {
        assertThat(UploadPolicy.from(apiValue)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "ANYONE, everyone",
        "HOST_ONLY, host",
    })
    void 열거형을_API_문자열로_바꾼다(UploadPolicy policy, String expected) {
        assertThat(policy.apiValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVERYONE", "Everyone", "  everyone  "})
    void 대소문자와_앞뒤_공백은_무시한다(String apiValue) {
        assertThat(UploadPolicy.from(apiValue)).isEqualTo(UploadPolicy.ANYONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "anyone", "HOST_ONLY", "hosts"})
    void 알_수_없는_값이면_예외(String apiValue) {
        assertThatThrownBy(() -> UploadPolicy.from(apiValue))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }

    @Test
    void null_이면_예외() {
        assertThatThrownBy(() -> UploadPolicy.from(null))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }
}