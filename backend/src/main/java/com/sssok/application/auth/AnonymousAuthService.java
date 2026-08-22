package com.sssok.application.auth;

import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.port.out.TokenProvider.IssuedToken;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 익명 회원 생성 및 토큰 발급. 호출할 때마다 새 회원(새 userId)이 생긴다.
@Service
@RequiredArgsConstructor
public class AnonymousAuthService {

    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    public AuthResult authenticate(String nickname) {
        Instant now = Instant.now();
        Member member = memberRepository.save(Member.register(new Nickname(nickname), now));
        IssuedToken token = tokenProvider.issue(member.getId(), now);
        return new AuthResult(token.value(), member.getId(), member.getDisplayName().value(), token.expiresAt());
    }
}
