package com.sssok.renewal.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void 익명_사용자의_가입은_식별자와_기존_정보를_유지한다() {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW);

        member.completeRegistration();

        assertThat(member.getId()).isEqualTo(10L);
        assertThat(member.getDisplayName().value()).isEqualTo("로지");
        assertThat(member.getCreatedAt()).isEqualTo(NOW);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(member.isActive()).isTrue();
    }

    @Test
    void 통합한_익명_사용자는_비활성_상태가_된다() {
        Member member = Member.register(new Nickname("로지"), NOW);
        member.markMerged();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(member.isActive()).isFalse();
    }

    @Test
    void 탈퇴한_회원은_비활성_상태가_된다() {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW, MemberStatus.REGISTERED);
        member.withdraw();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"REGISTERED", "MERGED", "WITHDRAWN"})
    void 익명이_아니면_가입하거나_통합할_수_없다(MemberStatus status) {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW, status);

        assertThatThrownBy(member::completeRegistration).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(member::markMerged).isInstanceOf(IllegalStateException.class);
        assertThat(member.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"ANONYMOUS", "MERGED", "WITHDRAWN"})
    void 가입한_회원만_탈퇴할_수_있다(MemberStatus status) {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW, status);

        assertThatThrownBy(member::withdraw).isInstanceOf(IllegalStateException.class);
        assertThat(member.getStatus()).isEqualTo(status);
    }

    @Test
    void 등록_직후에는_아직_식별자가_없다() {
        Member member = Member.register(new Nickname("로지"), NOW);

        assertThat(member.getId()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ANONYMOUS);
        assertThat(member.isActive()).isTrue();
        assertThat(member.getDisplayName().value()).isEqualTo("로지");
        assertThat(member.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 저장된_값으로_복원할_수_있다() {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW);

        assertThat(member.getId()).isEqualTo(10L);
    }

    @Test
    void 같은_식별자인지_판별할_수_있다() {
        Member member = Member.reconstruct(10L, new Nickname("로지"), NOW);

        assertThat(member.isSame(10L)).isTrue();
        assertThat(member.isSame(11L)).isFalse();
    }

    @Test
    void 저장되지_않은_회원은_어떤_식별자와도_같지_않다() {
        Member member = Member.register(new Nickname("로지"), NOW);

        assertThat(member.isSame(10L)).isFalse();
    }
}
