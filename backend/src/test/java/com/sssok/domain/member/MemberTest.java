package com.sssok.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void 등록_직후에는_아직_식별자가_없다() {
        Member member = Member.register(new Nickname("로지"), NOW);

        assertThat(member.getId()).isNull();
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
