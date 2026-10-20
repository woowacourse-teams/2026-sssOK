package com.sssok.support;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

// FK가 있는 미디어/폴더 테스트는 참조할 방도 준비해야 한다. 운영 데이터나 제약은 변경하지 않는다.
public final class UploadRoomFixtures {
    private UploadRoomFixtures() { }

    public static void ensure(JdbcTemplate jdbc, long... roomIds) {
        for (long id : roomIds) {
            if (exists(jdbc, id)) { continue; }
            // 1·2 같은 작은 ID는 identity로 먼저 할당해 이후 서비스의 자동 생성과 충돌하지 않는다.
            if (id <= 2) {
                long generated;
                do { generated = insertGenerated(jdbc); } while (generated < id);
                if (exists(jdbc, id)) { continue; }
            }
            jdbc.update("""
                INSERT INTO room (id, code, name, status, upload_policy, host_id, created_at, updated_at, version)
                VALUES (?, ?, '테스트 방', 'ACTIVE', 'ANYONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, code());
        }
        String product = jdbc.execute((java.sql.Connection connection) -> connection.getMetaData().getDatabaseProductName());
        if ("PostgreSQL".equals(product)) {
            jdbc.queryForObject("SELECT setval('room_id_seq', GREATEST((SELECT last_value FROM room_id_seq), "
                + "(SELECT COALESCE(MAX(id), 1) FROM room)))", Long.class);
        }
    }

    private static boolean exists(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM room WHERE id = ?", Long.class, id) > 0;
    }

    private static long insertGenerated(JdbcTemplate jdbc) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO room (code, name, status, upload_policy, host_id, created_at, updated_at, version)
                VALUES (?, '테스트 방', 'ACTIVE', 'ANYONE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, new String[]{"id"});
            statement.setString(1, code());
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static String code() { return UUID.randomUUID().toString().substring(0, 8); }
}
