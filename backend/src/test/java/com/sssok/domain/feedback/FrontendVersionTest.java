package com.sssok.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FrontendVersionTest {

    @Test
    void 프론트가_보낸_버전_태그를_그대로_보관한다() {
        assertThat(FrontendVersion.from("fe-v0.1.0").value()).isEqualTo("fe-v0.1.0");
    }

    @Test
    void 앞뒤_공백을_제거한다() {
        assertThat(FrontendVersion.from("  fe-v0.1.0  ").value()).isEqualTo("fe-v0.1.0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 헤더가_없거나_비어_있으면_알_수_없음으로_둔다(String raw) {
        FrontendVersion frontendVersion = FrontendVersion.from(raw);

        assertThat(frontendVersion.isUnknown()).isTrue();
        assertThat(frontendVersion.value()).isNull();
    }

    @Test
    void 최대_길이를_넘으면_잘라서_담는다() {
        FrontendVersion frontendVersion = FrontendVersion.from("v".repeat(FrontendVersion.MAX_LENGTH + 10));

        assertThat(frontendVersion.value()).hasSize(FrontendVersion.MAX_LENGTH);
    }

    @Test
    void 정확히_최대_길이면_자르지_않는다() {
        FrontendVersion frontendVersion = FrontendVersion.from("v".repeat(FrontendVersion.MAX_LENGTH));

        assertThat(frontendVersion.value()).hasSize(FrontendVersion.MAX_LENGTH);
    }

    @Test
    void 형식은_검증하지_않는다() {
        // 클라이언트가 보내는 값이라 어차피 신뢰할 수 없고, 서버가 패턴을 강제하면
        // 버전 체계가 바뀔 때마다 서버를 고쳐야 한다. 길이만 제한한다.
        assertThat(FrontendVersion.from("아무거나").value()).isEqualTo("아무거나");
    }
}
