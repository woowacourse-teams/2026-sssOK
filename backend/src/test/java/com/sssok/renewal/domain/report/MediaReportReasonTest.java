package com.sssok.renewal.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.report.exception.InvalidMediaReportReasonException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaReportReasonTest {

    @ParameterizedTest
    @EnumSource(MediaReportReason.class)
    void 정의된_사유를_받는다(MediaReportReason reason) {
        assertThat(MediaReportReason.from(reason.name())).isEqualTo(reason);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "SPAM", "harassment", "Privacy_Violation"})
    void 정의되지_않은_사유는_거절한다(String value) {
        assertThatThrownBy(() -> MediaReportReason.from(value))
            .isInstanceOf(InvalidMediaReportReasonException.class);
    }
}
