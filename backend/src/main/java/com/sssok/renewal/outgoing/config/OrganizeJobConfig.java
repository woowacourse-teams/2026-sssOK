package com.sssok.renewal.outgoing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

// 작업 기능을 켠 환경에서만 필수 설정을 검사한다. 켜져 있는데 내부 토큰이 없으면 기동할 때 바로 실패한다.
@Configuration
@ConditionalOnProperty(name = "gallery.organization.job.enabled", havingValue = "true")
public class OrganizeJobConfig {

    public OrganizeJobConfig(OrganizeJobProperties properties) {
        properties.requireValid();
    }
}
