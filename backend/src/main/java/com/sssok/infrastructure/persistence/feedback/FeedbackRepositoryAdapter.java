package com.sssok.infrastructure.persistence.feedback;

import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.feedback.FrontendVersion;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.UserAgent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
    public Optional<Feedback> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Feedback> findLatest(int limit) {
        return toDomains(jpaRepository.findAllByOrderByCreatedAtDescIdDesc(Limit.of(limit)));
    }

    @Override
    public List<Feedback> findLatestBefore(Instant createdAt, Long id, int limit) {
        return toDomains(jpaRepository.findAllBefore(createdAt, id, Limit.of(limit)));
    }

    @Override
    public Optional<Feedback> findLatestByMemberIdSince(Long memberId, Instant since) {
        return jpaRepository
            .findFirstByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(memberId, since)
            .map(this::toDomain);
    }

    private List<Feedback> toDomains(List<FeedbackJpaEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
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
