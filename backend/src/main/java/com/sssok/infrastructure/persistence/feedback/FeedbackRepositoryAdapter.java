package com.sssok.infrastructure.persistence.feedback;

import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.feedback.FrontendVersion;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.UserAgent;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackRepositoryAdapter implements FeedbackRepository {

    private final FeedbackJpaRepository jpaRepository;

    @Override
    public Feedback save(Feedback feedback) {
        return toDomain(jpaRepository.save(toEntity(feedback)));
    }

    @Override
    public Optional<Feedback> findLatestByMemberIdSince(Long memberId, Instant since) {
        return jpaRepository
            .findFirstByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(memberId, since)
            .map(this::toDomain);
    }

    private FeedbackJpaEntity toEntity(Feedback feedback) {
        return new FeedbackJpaEntity(
            feedback.getId(),
            feedback.getContent().value(),
            feedback.getRoomId(),
            feedback.getRoomName(),
            feedback.getMemberId(),
            feedback.getNickname(),
            feedback.getUserAgent().value(),
            feedback.getFrontendVersion().value(),
            feedback.getCreatedAt()
        );
    }

    private Feedback toDomain(FeedbackJpaEntity entity) {
        return Feedback.reconstruct(
            entity.getId(),
            new FeedbackContent(entity.getContent()),
            entity.getRoomId(),
            entity.getRoomName(),
            entity.getMemberId(),
            entity.getNickname(),
            UserAgent.from(entity.getUserAgent()),
            FrontendVersion.from(entity.getFrontendVersion()),
            entity.getCreatedAt()
        );
    }
}
