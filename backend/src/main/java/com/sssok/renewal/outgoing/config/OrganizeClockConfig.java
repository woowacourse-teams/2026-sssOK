package com.sssok.renewal.outgoing.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 사진 정리 작업의 만료·간격·재시도 대기가 같은 시계를 쓰게 한다. 테스트는 이 빈을 고정·이동 가능한 시계로 바꿔 시간을 제어한다.
@Configuration
public class OrganizeClockConfig {

    @Bean(name = "organizeClock")
    @ConditionalOnMissingBean(name = "organizeClock")
    public Clock organizeClock() {
        return Clock.systemUTC();
    }
}
