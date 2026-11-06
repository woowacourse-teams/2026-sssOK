package com.sssok.renewal.application.organization;

import com.sssok.support.PostgresIntegrationTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

// 사진 정리 중 PostgreSQL 전용 쿼리(폴더 담기의 ON CONFLICT 등)를 거치는 서비스 통합 테스트의 공통 설정.
// 설정은 OrganizeH2Test 와 같게 둔다. 일반 규칙은 H2 에서, DB 종속 경로만 여기서 검증한다.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@PostgresIntegrationTest
@Import({OrganizeTestClockConfig.class, OrganizeJobStateHelper.class, OrganizeFixtures.class})
@TestPropertySource(properties = {
    "gallery.organization.job.enabled=true",
    "gallery.organization.job.internal-token=event-token",
    "gallery.organization.job.max-photos=5",
    "gallery.organization.job.start-interval=60s",
    "gallery.organization.job.dispatch.sweep-interval=1h",
    "gallery.organization.mcp.token=mcp-token"
})
public @interface OrganizePostgresTest {
}
