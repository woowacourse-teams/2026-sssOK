package com.sssok.infrastructure.persistence.auth;

import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkCodeRepositoryAdapter implements LinkCodeRepository {

    private final LinkCodeJpaRepository jpaRepository;

    @Override
    public LinkCode save(LinkCode linkCode) {
        LinkCodeJpaEntity saved = jpaRepository.save(toEntity(linkCode));
        return toDomain(saved);
    }

    @Override
    public void deleteAllByMemberId(Long memberId) {
        jpaRepository.deleteAllByMemberId(memberId);
    }

    @Override
    public Optional<LinkCode> findByCode(LinkCodeValue code) {
        return jpaRepository.findByCode(code.value()).map(this::toDomain);
    }

    @Override
    public boolean deleteByCode(LinkCodeValue code) {
        return jpaRepository.deleteByCode(code.value()) > 0;
    }

    private LinkCodeJpaEntity toEntity(LinkCode linkCode) {
        return new LinkCodeJpaEntity(
            linkCode.getId(),
            linkCode.getMemberId(),
            linkCode.getCode().value(),
            linkCode.getExpiresAt()
        );
    }

    private LinkCode toDomain(LinkCodeJpaEntity entity) {
        return LinkCode.reconstruct(
            entity.getId(),
            entity.getMemberId(),
            new LinkCodeValue(entity.getCode()),
            entity.getExpiresAt()
        );
    }
}
