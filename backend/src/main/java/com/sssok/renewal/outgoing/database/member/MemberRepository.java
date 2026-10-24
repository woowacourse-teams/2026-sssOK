package com.sssok.renewal.outgoing.database.member;

import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberRepository {

    private final MemberJpaRepository jpaRepository;

    public Member save(Member member) {
        MemberJpaEntity saved = jpaRepository.save(toEntity(member));
        return toDomain(saved);
    }

    public Optional<Member> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    public boolean lockById(Long id) {
        return jpaRepository.findWithLockById(id).isPresent();
    }

    public List<Member> findAllByIdIn(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(memberIds).stream().map(this::toDomain).toList();
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
