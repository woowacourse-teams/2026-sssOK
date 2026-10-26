package com.sssok.renewal.application.auth;

import com.sssok.application.room.DeleteRoomService;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.SocialAccount;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.database.member.WithdrawalRoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawMemberService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final WithdrawalRoomRepository withdrawalRoomRepository;
    private final DeleteRoomService deleteRoomService;
    private final SessionTokenProvider sessionTokenProvider;

    @Transactional
    public List<SocialAccount> withdraw(Long memberId) {
        Member member = lockMember(memberId);
        if (member.getStatus() != MemberStatus.REGISTERED) {
            throw SocialAuthException.conflict();
        }
        List<SocialAccount> socialAccounts = socialAccountRepository.findByMemberId(memberId);
        if (socialAccounts.isEmpty()) {
            throw SocialAuthException.conflict();
        }

        for (Long roomId : withdrawalRoomRepository.findUndeletedHostedRoomIds(memberId)) {
            deleteRoomService.delete(roomId, memberId);
        }
        member.withdraw();
        memberRepository.save(member);
        sessionTokenProvider.revokeAll(memberId);
        // WITHDRAWN 회원에 남은 소셜 계정이 연결 해제 대기 기록이다. 토큰도 성공 전까지 보존한다.
        return socialAccounts;
    }

    @Transactional
    public void completeUnlink(SocialAccount socialAccount) {
        Member member = lockMember(socialAccount.memberId());
        if (member.getStatus() != MemberStatus.WITHDRAWN) {
            throw SocialAuthException.conflict();
        }
        // 새로 가입한 계정을 건드리지 않도록 원래 계정 행 ID와 회원 ID로 삭제한다.
        socialAccountRepository.deleteByIdAndMemberId(socialAccount.id(), socialAccount.memberId());
    }

    private Member lockMember(Long memberId) {
        if (!memberRepository.lockById(memberId)) {
            throw SocialAuthException.invalid();
        }
        return memberRepository.findById(memberId).orElseThrow(SocialAuthException::invalid);
    }
}
