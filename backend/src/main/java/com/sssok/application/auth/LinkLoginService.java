package com.sssok.application.auth;

import com.sssok.application.auth.exception.LinkCodeExpiredException;
import com.sssok.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.application.port.out.TokenProvider.IssuedToken;
import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import com.sssok.domain.member.Member;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 연결 코드로 원래 계정의 새 토큰을 발급받는다. 코드는 1회 사용 후 즉시 폐기한다.
@Service
@RequiredArgsConstructor
public class LinkLoginService {

    private final LinkCodeRepository linkCodeRepository;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    // LinkCodeExpiredException을 던질 때도 그 직전의 청소용 삭제는 커밋돼야 하므로 롤백 대상에서 뺌
    @Transactional(noRollbackFor = LinkCodeExpiredException.class)
    public AuthResult login(String rawCode) {
        Instant now = Instant.now();
        LinkCodeValue code = new LinkCodeValue(rawCode);

        LinkCode linkCode = linkCodeRepository.findByCode(code)
            .orElseThrow(LinkCodeNotFoundException::new);
        if (linkCode.isExpired(now)) {
            linkCodeRepository.deleteByCode(code);
            throw new LinkCodeExpiredException();
        }

        // 삭제가 실제로 이 행을 지웠는지로 소비 성공 여부를 판별
        boolean consumed = linkCodeRepository.deleteByCode(code);
        if (!consumed) {
            throw new LinkCodeNotFoundException();
        }

        Member member = memberRepository.findById(linkCode.getMemberId()).orElseThrow();
        IssuedToken token = tokenProvider.issue(member.getId(), now);
        return new AuthResult(token.value(), member.getId(), member.getDisplayName().value(), token.expiresAt());
    }
}
