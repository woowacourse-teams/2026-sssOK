package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.member.MemberJpaEntity;
import com.sssok.renewal.outgoing.database.member.MemberJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UploadMemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    public Optional<Member> findById(Long memberId) {
        return memberJpaRepository.findById(memberId).map(this::toDomain);
    }

    private Member toDomain(MemberJpaEntity entity) {
        return Member.reconstruct(entity.getId(), new Nickname(entity.getNickname()), entity.getCreatedAt());
    }
}
