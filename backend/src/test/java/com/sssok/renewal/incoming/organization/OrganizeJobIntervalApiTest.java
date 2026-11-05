package com.sssok.renewal.incoming.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// OAuth 회원의 새 작업 접수 간격(60초)을 실제 값으로 검증한다. 다른 시나리오는 간격을 0으로 둔 컨텍스트를 쓴다.
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==",
    "gallery.organization.job.enabled=true",
    "gallery.organization.job.internal-token=event-token",
    "gallery.organization.job.max-photos=5",
    "gallery.organization.job.start-interval=60s",
    "gallery.organization.job.dispatch.sweep-interval=1h",
    "gallery.organization.mcp.token=mcp-token"
})
class OrganizeJobIntervalApiTest extends OrganizeJobApiSupport {

    @Test
    void 간격_안에_다시_접수하면_429와_남은_초를_알리고_작업을_만들지_않는다() throws Exception {
        jpegs(2);
        String jobId = startPerson();
        awaitStatus(jobId, "RUNNING");
        mockMvc.perform(post(BASE + "/{jobId}/cancel", roomId, jobId).header("Authorization", hostToken))
            .andExpect(status().isOk());

        startByKind(hostToken, "PERSON").andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("ORGANIZE_RATE_LIMITED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_job WHERE room_id = ?", Long.class, roomId))
            .isEqualTo(1);
        // 간격은 회원 기준이라 다른 회원은 영향을 받지 않는다.
        startByKind(guestToken, "PERSON").andExpect(status().isAccepted());
    }

    @Test
    void 검증에서_거절된_요청은_간격을_시작하지_않는다() throws Exception {
        startByKind(hostToken, "PERSON").andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_start_gate WHERE member_id = ?", Long.class,
            hostId)).isZero();

        jpegs(2);
        startByKind(hostToken, "PERSON", UUID.randomUUID().toString()).andExpect(status().isAccepted());
    }
}
