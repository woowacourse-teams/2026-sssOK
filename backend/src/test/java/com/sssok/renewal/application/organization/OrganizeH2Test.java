package com.sssok.renewal.application.organization;

import com.sssok.support.H2IntegrationTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

// 사진 정리 Repository + Service 통합 테스트(H2)의 공통 설정. 모든 테스트가 같은 설정을 써야 Spring 컨텍스트를 함께 쓴다.
// 스위퍼는 길게 늦춰 테스트가 직접 부르는 호출과 겹치지 않게 하고, 시계는 MutableClock 으로 바꾼다.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@H2IntegrationTest
@Import({OrganizeTestClockConfig.class, OrganizeJobStateHelper.class, OrganizeFixtures.class})
@TestPropertySource(properties = {
    "gallery.organization.job.enabled=true",
    "gallery.organization.job.internal-token=event-token",
    "gallery.organization.job.max-photos=5",
    "gallery.organization.job.start-interval=60s",
    "gallery.organization.job.dispatch.sweep-interval=1h",
    "gallery.organization.mcp.token=mcp-token"
})
public @interface OrganizeH2Test {
}
