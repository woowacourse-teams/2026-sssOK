package com.sssok.renewal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.UploadMemberRepository;
import com.sssok.support.H2IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@H2IntegrationTest
class MemberLifecyclePersistenceTest {

    @Autowired
    MemberRepository members;

    @Autowired
    UploadMemberRepository uploadMembers;

    @Test
    void 가입과_탈퇴_상태가_기존과_신규_업로드_조회_경로에_반영된다() {
        Member member = members.save(Member.register(new Nickname("로지"), Instant.now()));
        Long originalId = member.getId();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ANONYMOUS);

        member.completeRegistration();
        members.save(member);
        Member registered = members.findById(originalId).orElseThrow();
        assertThat(registered.getStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(uploadMembers.findById(originalId).orElseThrow().getStatus())
            .isEqualTo(MemberStatus.REGISTERED);

        registered.withdraw();
        members.save(registered);
        assertThat(members.findById(originalId).orElseThrow().getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(uploadMembers.findById(originalId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void 통합한_익명_사용자를_조회해도_활성_상태로_돌아가지_않는다() {
        Member member = members.save(Member.register(new Nickname("로지"), Instant.now()));
        member.markMerged();
        members.save(member);

        assertThat(members.findById(member.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(uploadMembers.findById(member.getId()).orElseThrow().isActive()).isFalse();
    }
}
