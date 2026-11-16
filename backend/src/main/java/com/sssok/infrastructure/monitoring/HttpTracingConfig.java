package com.sssok.infrastructure.monitoring;

import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

@Configuration(proxyBeanMethods = false)
public class HttpTracingConfig {

    @Bean
    ServerRequestObservationConvention serverRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                // 원문 URL/쿼리의 토큰·방 코드를 내보내지 않는다. HTTP 메서드·상태·라우트는 유지한다.
                return KeyValues.empty();
            }
        };
    }
}
