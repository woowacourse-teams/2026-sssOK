package com.sssok.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.feedback.exception.InvalidFeedbackAssigneeException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackAssigneeTest {

    @Test
    void 이름으로_담당자를_찾는다() {
        assertThat(FeedbackAssignee.from("MAICHAN")).isEqualTo(FeedbackAssignee.MAICHAN);
        assertThat(FeedbackAssignee.from("HAENI")).isEqualTo(FeedbackAssignee.HAENI);
        assertThat(FeedbackAssignee.from("YUNDOL")).isEqualTo(FeedbackAssignee.YUNDOL);
        assertThat(FeedbackAssignee.from("HYUNMIBAP")).isEqualTo(FeedbackAssignee.HYUNMIBAP);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"yundol", "윤돌", " YUNDOL", "GAHYEON"})
    void 정해지지_않은_이름이면_예외(String name) {
        assertThatThrownBy(() -> FeedbackAssignee.from(name))
            .isInstanceOf(InvalidFeedbackAssigneeException.class);
    }

    @Test
    void 새로_작성한_의견은_담당자가_없다() {
        assertThat(의견().getAssignee()).isNull();
    }

    @Test
    void 담당자를_정했다가_바꾸고_비울_수_있다() {
        Feedback feedback = 의견();

        feedback.changeAssignee(FeedbackAssignee.YUNDOL);
        assertThat(feedback.getAssignee()).isEqualTo(FeedbackAssignee.YUNDOL);

        feedback.changeAssignee(FeedbackAssignee.HAENI);
        assertThat(feedback.getAssignee()).isEqualTo(FeedbackAssignee.HAENI);

        feedback.changeAssignee(null);
        assertThat(feedback.getAssignee()).isNull();
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
