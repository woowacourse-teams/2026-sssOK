package com.sssok.support;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration-postgres")
class VectorExtensionMigrationTest extends PostgresContainerSupport {

    @Test
    void 빈_DB에_전체_마이그레이션을_적용하면_벡터_유사도를_계산할_수_있다() throws Exception {
        // 공유 테스트 DB 대신 별도 스키마에서 전체 Flyway 이력을 검증한다.
        String schema = "vector_migration_test";
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            Statement statement = connection.createStatement()) {
            try {
                Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .load()
                    .migrate();

                statement.execute("SET search_path TO " + schema + ", public");
                try (ResultSet result = statement.executeQuery("""
                    SELECT 1 - ('[1,0,0]'::vector <=> '[1,0,0]'::vector) AS identical,
                           1 - ('[1,0,0]'::vector <=> '[0,1,0]'::vector) AS unrelated
                    """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getDouble("identical")).isEqualTo(1.0);
                    assertThat(result.getDouble("unrelated")).isEqualTo(0.0);
                }
                assertThat(Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).load().migrate().migrationsExecuted).isZero();
            } finally {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
