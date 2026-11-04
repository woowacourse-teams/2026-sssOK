package com.sssok.renewal.application.organization;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// 운영의 organizeClock 을 제어 가능한 시계로 바꾼다(빈 이름이 같아 덮어쓴다).
@TestConfiguration
public class OrganizeTestClockConfig {

    @Bean(name = "organizeClock")
    public MutableClock organizeClock() {
        return new MutableClock();
    }
}
