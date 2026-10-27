package com.sssok.infrastructure.persistence.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@PostgresIntegrationTest
class MediaUploaderNicknameMigrationTest extends PostgresContainerSupport {

    @Autowired
    DataSource dataSource;

    @Test
    void 기존_사진은_방_닉네임을_우선하고_없으면_계정_이름으로_보완한다() throws Exception {
        try (var connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                String schema = "nickname_migration_" + UUID.randomUUID().toString().replace("-", "");
                statement.execute("CREATE SCHEMA " + schema);
                connection.setSchema(schema);
                statement.execute("CREATE TABLE member (id BIGINT, nickname VARCHAR(12))");
                statement.execute("CREATE TABLE room_member (room_id BIGINT, member_id BIGINT, nickname VARCHAR(12))");
                statement.execute("CREATE TABLE stored_file (id BIGINT, room_id BIGINT, uploader_id BIGINT)");
                statement.execute("INSERT INTO member VALUES (1, '기본이름'), (2, '다른회원')");
                statement.execute("INSERT INTO room_member VALUES (10, 1, '방별명')");
                statement.execute("INSERT INTO stored_file VALUES (1, 10, 1), (2, 20, 1), (3, 10, 2), (4, 10, 3)");

                ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V47__add_media_uploader_nickname.sql"));

                try (var rows = statement.executeQuery("SELECT uploader_nickname FROM stored_file ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("방별명");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("기본이름");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("다른회원");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                connection.rollback();
                connection.setSchema(originalSchema);
            }
        }
    }
}
