package com.sssok.renewal.application.auth;

import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.security.TokenProvider;
import com.sssok.renewal.outgoing.security.TokenProvider.IssuedToken;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 익명 회원 생성 및 토큰 발급. 호출할 때마다 새 회원(새 userId)이 생긴다.
@Service
@RequiredArgsConstructor
public class AnonymousAuthService {

    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    @Transactional
    public AuthResult authenticate(String nickname) {
        Instant now = Instant.now();
        Member member = memberRepository.save(Member.register(new Nickname(nickname), now));
        IssuedToken token = tokenProvider.issue(member.getId(), now);
        return new AuthResult(token.value(), member.getId(), member.getDisplayName().value(), token.expiresAt());
    }
}
