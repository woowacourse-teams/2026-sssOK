package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.outgoing.database.member.MemberActivityRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MergeAnonymousMemberService {

    private final MemberRepository memberRepository;
    private final MemberActivityRepository memberActivityRepository;
    private final SessionTokenProvider sessionTokenProvider;

    // 로그인 트랜잭션에 참여한다. 활동 이전·회원 상태·인증 변경을 함께 롤백한다.
    @Transactional
    public Member merge(Long anonymousMemberId, Long registeredMemberId) {
        lockMembers(anonymousMemberId, registeredMemberId);
        Member anonymousMember = requireMemberStatus(anonymousMemberId, MemberStatus.ANONYMOUS);
        Member registeredMember = requireMemberStatus(registeredMemberId, MemberStatus.REGISTERED);

        mergeRoomMemberships(anonymousMemberId, registeredMemberId);
        mergeLikes(anonymousMemberId, registeredMemberId);
        transferOwnership(anonymousMemberId, registeredMemberId);

        // 의견은 계정에 연결하되 작성 당시 닉네임·방 이름은 그대로 보존한다.
        memberActivityRepository.transferFeedbackAuthor(anonymousMemberId, registeredMemberId);
        anonymousMember.markMerged();
        memberRepository.save(anonymousMember);
        sessionTokenProvider.revokeAll(anonymousMemberId);
        return registeredMember;
    }

    private void mergeRoomMemberships(Long anonymousMemberId, Long registeredMemberId) {
        // 같은 방에 참여했다면 더 이른 입장 시각을 유지하고 익명 참여 기록을 제거한다.
        memberActivityRepository.mergeRoomMemberships(anonymousMemberId, registeredMemberId);
        memberActivityRepository.deleteRoomMemberships(anonymousMemberId);
    }

    private void mergeLikes(Long anonymousMemberId, Long registeredMemberId) {
        // 같은 사진의 좋아요는 한 건으로 합친다.
        memberActivityRepository.mergeLikes(anonymousMemberId, registeredMemberId);
        memberActivityRepository.deleteLikes(anonymousMemberId);
    }

    private void transferOwnership(Long anonymousMemberId, Long registeredMemberId) {
        memberActivityRepository.transferHostedRooms(anonymousMemberId, registeredMemberId);
        memberActivityRepository.transferUploads(anonymousMemberId, registeredMemberId);
        // 로그인 뒤에도 진행 중인 다운로드 결과를 조회할 수 있게 요청자를 이전한다.
        memberActivityRepository.transferDownloadJobs(anonymousMemberId, registeredMemberId);
    }

    private void lockMembers(Long anonymousMemberId, Long registeredMemberId) {
        if (anonymousMemberId.equals(registeredMemberId)) {
            throw SocialAuthException.conflict();
        }
        // 여러 통합 요청도 항상 ID 오름차순으로 잠가 교착을 피한다.
        if (!memberRepository.lockById(Math.min(anonymousMemberId, registeredMemberId))
            || !memberRepository.lockById(Math.max(anonymousMemberId, registeredMemberId))) {
            throw SocialAuthException.invalid();
        }
    }

    private Member requireMemberStatus(Long memberId, MemberStatus expectedStatus) {
        Member member = memberRepository.findById(memberId).orElseThrow(SocialAuthException::invalid);
        if (member.getStatus() != expectedStatus) {
            throw SocialAuthException.conflict();
        }
        return member;
    }
}
