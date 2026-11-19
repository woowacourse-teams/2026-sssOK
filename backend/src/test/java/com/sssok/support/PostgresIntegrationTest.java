package com.sssok.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("integration-postgres")
// 캐시된 테스트 컨텍스트의 자동 워커가 공유 PostgreSQL 큐의 다른 테스트 작업을 선점하지 않게 한다.
// 작업 선점·처리·복구는 각 테스트가 직접 호출해 검증한다.
@SpringBootTest(properties = "media.thumbnail.auto-generate=false")
public @interface PostgresIntegrationTest {
}
