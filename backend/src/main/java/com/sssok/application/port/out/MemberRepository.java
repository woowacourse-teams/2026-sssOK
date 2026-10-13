package com.sssok.application.port.out;

import com.sssok.domain.member.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 회원 영속화 출력
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    // 요청자 단위로 처리해야 하는 쓰기 작업을 직렬화할 때 사용한다.
    boolean lockById(Long id);

    // 미디어 목록에 업로더 이름을 붙일 때 쓴다. 하나씩 찾으면 미디어 수만큼 쿼리가 나간다.
    List<Member> findAllByIdIn(Collection<Long> memberIds);
}
