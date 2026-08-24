package com.sssok.application.port.out;

import com.sssok.domain.member.Member;
import java.util.Optional;

// 회원 영속화 출력
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);
}
