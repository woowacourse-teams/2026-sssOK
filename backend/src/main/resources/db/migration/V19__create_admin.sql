-- #202 관리자 계정.
--
-- member 테이블에 얹지 않고 따로 둔다. RoomPurger 가 방 정리 때 그 방에만 속한 회원을 지우는데,
-- 같은 테이블을 쓰면 관리자 계정이 그 배치에 휩쓸릴 수 있다.
CREATE TABLE admin (
    id            BIGSERIAL PRIMARY KEY,
    login_id      VARCHAR(20) NOT NULL,
    -- BCrypt 해시. 평문은 어디에도 저장하지 않는다. 해시 길이는 항상 60자다.
    password_hash VARCHAR(60) NOT NULL,
    name          VARCHAR(20) NOT NULL,
    -- SUPER_ADMIN 또는 ADMIN. 역할이 늘어도 이 칼럼은 그대로고 AdminRole 에 항목만 추가한다.
    role          VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_admin_login_id UNIQUE (login_id)
);

-- 초기 계정 4개. 비밀번호는 미리 만든 BCrypt 해시로만 넣는다 — 평문을 마이그레이션에 남기면
-- 저장소 이력에 영구히 박힌다.
--
-- 팀에 전달한 초기 비밀번호는 각자 로그인한 뒤 계정 수정 API 로 바꾸는 것을 전제로 한다.
INSERT INTO admin (login_id, password_hash, name, role, created_at, updated_at) VALUES
    ('superadmin', '$2a$10$njykV68gcr5OsW0xqQJcBuNFjhYo3k2o/usKhk2NqPeY2Vc4prE8C', '마이찬', 'SUPER_ADMIN', now(), now()),
    ('hyeonmibap', '$2a$10$/YeicOd1K/aW2JaosPfdnOcT1c999gtgXy2.VFeUQ3YiqLzi6ZXAu', '현미밥', 'ADMIN', now(), now()),
    ('yundol', '$2a$10$NhiMtVJVN3WfwIm811D1vulumojKCbFSED4F6t.QsZJCaOzINSUSe', '윤돌', 'ADMIN', now(), now()),
    ('haeni', '$2a$10$7JFur1ZgzmxjhdXQLqexOOpgoILED2ERdJsHJxrV71OeV27fQLsrO', '해니', 'ADMIN', now(), now());
