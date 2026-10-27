package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.outgoing.database.member.MemberJpaEntity;
import com.sssok.renewal.outgoing.database.member.MemberJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationMemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    public Optional<String> findNickname(Long memberId) {
        return memberJpaRepository.findById(memberId).map(MemberJpaEntity::getNickname);
    }
}
