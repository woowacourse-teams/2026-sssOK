package com.sssok.renewal.outgoing.auth;

import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.SessionTokenProvider;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@PostgresIntegrationTest
@Transactional
class AuthSchemaTest extends PostgresContainerSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnonymousAuthService auth;
    @Autowired SessionTokenProvider sessions;

    @Test
    void 인증_외래키는_회원_삭제시_함께_삭제되도록_설정된다() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN "
            + "('fk_auth_session_member', 'fk_legacy_auth_revocation_member') "
            + "AND contype = 'f' AND confdeltype = 'c' AND convalidated", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN "
            + "('idx_auth_session_member', 'idx_auth_session_expiry')", Integer.class)).isEqualTo(2);
    }

    @Test
    void 회원이_삭제되면_세션과_구형_토큰_차단기록도_삭제된다() {
        Long memberId = auth.authenticate("스키마").userId();
        sessions.revokeAll(memberId);
        // JPA 변경을 반영한 뒤 실제 DB의 삭제 정책을 검증한다.
        entityManager.flush();
        assertThat(count("auth_session", memberId)).isEqualTo(1);
        assertThat(count("legacy_auth_revocation", memberId)).isEqualTo(1);
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
        assertThat(count("auth_session", memberId)).isZero();
        assertThat(count("legacy_auth_revocation", memberId)).isZero();
    }

    @Autowired EntityManager entityManager;

    @Test
    void 존재하지_않는_회원의_세션은_저장할_수_없다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO auth_session(id, member_id, expires_at) "
            + "VALUES ('invalid', ?, now())", Long.MAX_VALUE))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 회원_상태는_정의된_값만_허용한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO member(nickname, status, created_at, updated_at) "
            + "VALUES ('스키마', 'INVALID', now(), now())"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String table, Long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE member_id = ?", Integer.class, memberId);
    }
}
