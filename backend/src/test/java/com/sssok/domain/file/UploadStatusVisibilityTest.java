package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// 조회 API 가 어떤 상태를 내려보낼지 정하는 규칙.
class UploadStatusVisibilityTest {

    // 스토리지에 실물이 올라간 뒤의 상태들이다.
    @ParameterizedTest
    @EnumSource(value = UploadStatus.class, names = {"PROCESSING", "READY"})
    void 실물이_있는_상태는_조회에_노출된다(UploadStatus status) {
        assertThat(status.isVisible()).isTrue();
    }

    // RESERVED 는 서명 URL 만 나간 상태고, FAILED 는 전송이 끝내 실패한 상태라 둘 다 실물이 없다.
    // 내려보내면 클라이언트가 열 수 없는 빈 항목을 그리게 된다.
    @ParameterizedTest
    @EnumSource(value = UploadStatus.class, names = {"RESERVED", "FAILED"})
    void 실물이_없는_상태는_조회에_노출되지_않는다(UploadStatus status) {
        assertThat(status.isVisible()).isFalse();
    }

    // 상태를 새로 추가하면서 노출 여부를 정하지 않으면 여기서 걸린다.
    @Test
    void 노출_대상_목록과_판정이_일치한다() {
        for (UploadStatus status : UploadStatus.values()) {
            assertThat(status.isVisible())
                .isEqualTo(UploadStatus.visibleStatuses().contains(status));
        }
    }
}
