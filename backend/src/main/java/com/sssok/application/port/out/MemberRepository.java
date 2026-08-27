package com.sssok.application.port.out;

import com.sssok.domain.member.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 회원 영속화 출력
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    // 미디어 목록에 업로더 이름을 붙일 때 쓴다. 하나씩 찾으면 미디어 수만큼 쿼리가 나간다.
    List<Member> findAllByIdIn(Collection<Long> memberIds);

    void deleteAllByIdIn(Collection<Long> memberIds);
}
