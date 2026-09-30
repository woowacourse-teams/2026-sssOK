package com.sssok.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveValueMaskerTest {

    @Test
    @DisplayName("Bearer 토큰 값을 가린다")
    void masksBearerToken() {
        String masked = SensitiveValueMasker.mask("Authorization: Bearer abc.def.ghi 로 요청함");

        assertThat(masked).doesNotContain("abc.def.ghi");
        assertThat(masked).contains(SensitiveValueMasker.MASK);
    }

    @Test
    @DisplayName("헤더 이름 없이 Bearer 만 있어도 토큰을 가린다")
    void masksBearerTokenWithoutHeaderName() {
        assertThat(SensitiveValueMasker.mask("bearer zzz111yyy")).isEqualTo("bearer ***");
    }

    @Test
    @DisplayName("헤더 이름 없이 본문에 박힌 JWT 도 가린다")
    void masksBareJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Zm9vYmFy";

        assertThat(SensitiveValueMasker.mask("토큰 검증 실패: " + jwt)).doesNotContain(jwt);
    }

    @Test
    @DisplayName("R2 서명 URL 의 서명 값을 가린다")
    void masksPresignedUrlSignature() {
        String url = "https://r2.example.com/o.jpg?X-Amz-Credential=AKIA123%2F20260928&X-Amz-Signature=deadbeef";

        String masked = SensitiveValueMasker.mask(url);

        assertThat(masked).doesNotContain("deadbeef");
        assertThat(masked).doesNotContain("AKIA123");
        // 어떤 객체에 대한 URL 이었는지는 남아야 추적에 쓸 수 있다.
        assertThat(masked).contains("https://r2.example.com/o.jpg");
    }

    @Test
    @DisplayName("key=value 형태로 노출된 비밀번호·키를 가린다")
    void masksKeyValueSecrets() {
        assertThat(SensitiveValueMasker.mask("password=hunter2")).isEqualTo("password=***");
        assertThat(SensitiveValueMasker.mask("{\"secretKey\": \"s3cr3t\"}")).doesNotContain("s3cr3t");
        assertThat(SensitiveValueMasker.mask("jdbc:postgresql://db/sssok?user=sssok&password=pw123"))
            .doesNotContain("pw123");
    }

    @Test
    @DisplayName("민감하지 않은 문구는 그대로 둔다")
    void keepsOrdinaryMessage() {
        String message = "방 1234 의 미디어 30건을 조회했습니다";

        assertThat(SensitiveValueMasker.mask(message)).isEqualTo(message);
    }

    @Test
    @DisplayName("null 과 빈 문자열을 그대로 돌려준다")
    void handlesNullAndEmpty() {
        assertThat(SensitiveValueMasker.mask(null)).isNull();
        assertThat(SensitiveValueMasker.mask("")).isEmpty();
    }
}
