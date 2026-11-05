package com.sssok.renewal.outgoing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OrganizeJobPropertiesTest {
    private static OrganizeJobProperties defaults(String token) {
        return new OrganizeJobProperties(false, null, null, null, null, null, null, null, null, null, null, null, null, token);
    }

    @Test
    void 값이_없으면_합의된_기본값을_쓴다() {
        OrganizeJobProperties properties = defaults(null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.maxPhotos()).isEqualTo(200);
        assertThat(properties.rateLimitPerMinute()).isEqualTo(5);
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(3));
        assertThat(properties.review()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.idempotencyRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.startInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.maxUserRetries()).isEqualTo(3);
        assertThat(properties.photoUrlTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.photoSourceOf(OrganizeKind.PERSON)).isEqualTo(OrganizePhotoSource.PREVIEW);
        assertThat(properties.dispatch().deadline()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.dispatch().sweepInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.liveness().startDeadline()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.liveness().heartbeatTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(properties.liveness().runLimit()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void 종류와_도구_이름을_양방향으로_찾는다() {
        OrganizeJobProperties properties = defaults(null);

        assertThat(properties.toolNameOf(OrganizeKind.PERSON)).isEqualTo("group_photos_by_person");
        assertThat(properties.toolNameOf(OrganizeKind.SIMILAR)).isEqualTo("group_similar_photos");
        assertThat(properties.kindOf("group_similar_photos")).isEqualTo(OrganizeKind.SIMILAR);
        assertThat(properties.kindOf("delete_everything")).isNull();
    }

    @Test
    void 재시도_간격은_횟수가_늘어도_마지막_간격을_유지한다() {
        OrganizeJobProperties.Dispatch dispatch = defaults(null).dispatch();

        assertThat(dispatch.retryDelayAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(dispatch.retryDelayAfter(2)).isEqualTo(Duration.ofSeconds(15));
        assertThat(dispatch.retryDelayAfter(5)).isEqualTo(Duration.ofMinutes(5));
        assertThat(dispatch.retryDelayAfter(50)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void 사용자_재시도_대기는_발송_백오프의_앞_3단계를_순서대로_쓴다() {
        OrganizeJobProperties properties = defaults(null);

        assertThat(properties.userRetryDelay(0)).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.userRetryDelay(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.userRetryDelay(2)).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.userRetryDelay(9)).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void 켜는_시점에_내부_토큰이_없으면_거절한다() {
        assertThatThrownBy(() -> defaults(null).requireValid()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults(" ").requireValid()).isInstanceOf(IllegalArgumentException.class);
        defaults("secret").requireValid();
    }

    @Test
    void 문자열_표현에_토큰을_남기지_않는다() {
        assertThat(defaults("super-secret").toString()).doesNotContain("super-secret");
    }
}
