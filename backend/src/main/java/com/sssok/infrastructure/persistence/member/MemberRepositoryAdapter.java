package com.sssok.infrastructure.persistence.member;

import com.sssok.application.port.out.MemberRepository;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    @Override
    public Member save(Member member) {
        MemberJpaEntity saved = jpaRepository.save(toEntity(member));
        return toDomain(saved);
    }

    @Override
    public Optional<Member> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public void deleteAllByIdIn(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        jpaRepository.deleteAllByIdIn(memberIds);
    }

    private MemberJpaEntity toEntity(Member member) {
        return new MemberJpaEntity(
            member.getId(),
            member.getDisplayName().value(),
            member.getCreatedAt()
        );
    }

    private Member toDomain(MemberJpaEntity entity) {
        return Member.reconstruct(
            entity.getId(),
            new Nickname(entity.getNickname()),
            entity.getCreatedAt()
        );
    }
}
