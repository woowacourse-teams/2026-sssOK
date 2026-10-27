package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.SocialAccount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawAccountService {

    private final WithdrawMemberService withdrawMemberService;
    private final UnlinkSocialAccountService unlinkSocialAccountService;

    @Transactional(propagation = Propagation.NEVER)
    public void withdraw(Long memberId) {
        List<SocialAccount> socialAccounts = withdrawMemberService.withdraw(memberId);
        for (SocialAccount socialAccount : socialAccounts) {
            unlinkAfterWithdrawal(socialAccount);
        }
    }

    private void unlinkAfterWithdrawal(SocialAccount socialAccount) {
        try {
            unlinkSocialAccountService.unlink(socialAccount.identity());
            withdrawMemberService.completeUnlink(socialAccount);
        } catch (RuntimeException exception) {
            // 외부 오류·응답 유실·후속 DB 실패 모두 수동 확인한다. 토큰과 예외 원문은 로그에 남기지 않는다.
            log.warn("탈퇴 후 소셜 연결 정리 미완료: memberId={}, socialAccountId={}",
                socialAccount.memberId(), socialAccount.id());
        }
    }
}
