package com.sssok.application.auth;

import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 인증된 회원의 연결 코드를 발급한다. 동시에 유효한 코드는 최대 1개만 허용한다.
@Service
@RequiredArgsConstructor
public class IssueLinkCodeService {

    private final LinkCodeRepository linkCodeRepository;
    private final RandomGenerator randomGenerator = new SecureRandom();

    // 이전 코드 삭제 + 새 코드 저장이 하나의 원자적 단위로 묶여야 해서 명시적으로 트랜잭션을 연다.
    @Transactional
    public LinkCodeResult issue(Long memberId) {
        Instant now = Instant.now();
        linkCodeRepository.deleteAllByMemberId(memberId);

        LinkCodeValue code = LinkCodeValue.generate(randomGenerator);
        LinkCode linkCode = linkCodeRepository.save(LinkCode.issue(memberId, code, now));
        return new LinkCodeResult(linkCode.getCode().value(), linkCode.getExpiresAt());
    }
}
