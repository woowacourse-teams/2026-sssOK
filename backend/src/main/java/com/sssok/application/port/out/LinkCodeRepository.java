package com.sssok.application.port.out;

import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import java.util.Collection;
import java.util.Optional;

// 연결 코드 영속화 출력
public interface LinkCodeRepository {

    LinkCode save(LinkCode linkCode);

    // 한 회원은 동시에 최대 1개의 유효한 코드만 가진다 — 새로 발급하기 전에 이전 코드를 무효화한다.
    void deleteAllByMemberId(Long memberId);

    // 회원을 지울 때 그 회원의 코드도 함께 지운다. 남겨두면 없는 회원을 가리키는 행이 된다.
    void deleteAllByMemberIdIn(Collection<Long> memberIds);

    Optional<LinkCode> findByCode(LinkCodeValue code);

    // 1회용 코드 소비 — 실제로 이 호출이 행을 지웠으면(=소비에 성공했으면) true.
    // 동시에 같은 코드로 여러 요청이 들어와도 단 하나만 true를 받는다.
    boolean deleteByCode(LinkCodeValue code);
}
