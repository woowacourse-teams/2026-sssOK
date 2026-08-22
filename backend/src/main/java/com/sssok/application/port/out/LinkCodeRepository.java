package com.sssok.application.port.out;

import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import java.util.Optional;

// 연결 코드 영속화 출력
public interface LinkCodeRepository {

    LinkCode save(LinkCode linkCode);

    // 한 회원은 동시에 최대 1개의 유효한 코드만 가진다 — 새로 발급하기 전에 이전 코드를 무효화한다.
    void deleteAllByMemberId(Long memberId);

    Optional<LinkCode> findByCode(LinkCodeValue code);

    // 1회용 코드 소비 — 로그인에 성공하면 즉시 폐기한다.
    void deleteByCode(LinkCodeValue code);
}
