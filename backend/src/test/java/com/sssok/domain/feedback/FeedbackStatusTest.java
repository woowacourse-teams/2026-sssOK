package com.sssok.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.feedback.exception.InvalidFeedbackStatusException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackStatusTest {

    @Test
    void 이름으로_상태를_찾는다() {
        assertThat(FeedbackStatus.from("REFLECTED")).isEqualTo(FeedbackStatus.REFLECTED);
        assertThat(FeedbackStatus.from("UNREFLECTED")).isEqualTo(FeedbackStatus.UNREFLECTED);
        assertThat(FeedbackStatus.from("IN_PROGRESS")).isEqualTo(FeedbackStatus.IN_PROGRESS);
        assertThat(FeedbackStatus.from("ON_HOLD")).isEqualTo(FeedbackStatus.ON_HOLD);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"reflected", "DONE", " REFLECTED", "in_progress", "HOLD"})
    void 정해지지_않은_이름이면_예외(String name) {
        assertThatThrownBy(() -> FeedbackStatus.from(name))
            .isInstanceOf(InvalidFeedbackStatusException.class);
    }

    @Test
    void 새로_작성한_의견은_미반영_상태다() {
        Feedback feedback = 의견();

        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.UNREFLECTED);
    }

    @Test
    void 반영했다가_미반영으로_되돌릴_수_있다() {
        Feedback feedback = 의견();

        feedback.changeStatus(FeedbackStatus.REFLECTED);
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.REFLECTED);

        feedback.changeStatus(FeedbackStatus.UNREFLECTED);
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.UNREFLECTED);
    }

    @Test
    void 처리중이나_보류로_옮겼다가_다른_상태로_바꿀_수_있다() {
        Feedback feedback = 의견();

        feedback.changeStatus(FeedbackStatus.IN_PROGRESS);
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.IN_PROGRESS);

        feedback.changeStatus(FeedbackStatus.ON_HOLD);
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.ON_HOLD);

        feedback.changeStatus(FeedbackStatus.REFLECTED);
        assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.REFLECTED);
    }

    private Feedback 의견() {
        return Feedback.write(
            new FeedbackContent("썸네일이 늦게 떠요"),
            1L,
            "우테코 회식",
            1L,
            "가현",
            UserAgent.from(null),
            FrontendVersion.from(null),
            Instant.now()
        );
    }
}
