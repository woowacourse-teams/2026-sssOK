package com.sssok.application.port.out;

import com.sssok.domain.feedback.Feedback;
import java.time.Instant;
import java.util.Optional;

public interface FeedbackRepository {

    Feedback save(Feedback feedback);

    Optional<Feedback> findLatestByMemberIdSince(Long memberId, Instant since);
}
