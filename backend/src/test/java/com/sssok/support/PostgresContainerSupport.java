package com.sssok.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// Testcontainers "싱글톤 컨테이너" 패턴
// @Container(JUnit 확장의 클래스별 생명주기 관리) 대신 static 초기화 블록에서 한 번만 띄우고 stop() 안함
// JVM(테스트 실행) 하나에 컨테이너 하나만 뜨고, 이 클래스를 상속하는 모든 API 인수 테스트가 같은 컨테이너를 공유
// 정리는 Testcontainers 자체의 Ryuk 리소스 리퍼가 테스트 실행이 끝나면 자동으로 처리
public abstract class PostgresContainerSupport {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
