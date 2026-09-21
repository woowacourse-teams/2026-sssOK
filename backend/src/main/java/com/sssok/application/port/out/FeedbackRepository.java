package com.sssok.application.port.out;

import com.sssok.domain.feedback.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository {

    Feedback save(Feedback feedback);

    Optional<Feedback> findById(Long id);

    Optional<Feedback> findLatestByMemberIdSince(Long memberId, Instant since);

    // 최신순 첫 페이지.
    List<Feedback> findLatest(int limit);

    // 최신순 다음 페이지. 기준점을 ID 하나가 아니라 정렬 키인 (작성 시각, ID) 쌍으로 받는다.
    List<Feedback> findLatestBefore(Instant createdAt, Long id, int limit);
}
