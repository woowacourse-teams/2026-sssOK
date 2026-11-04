package com.sssok.renewal.incoming.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.renewal.application.organization.job.OrganizeEvent;
import com.sssok.renewal.application.organization.job.OrganizeEventAck;
import com.sssok.renewal.application.organization.job.OrganizeEventService;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.security.TokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 슬라이스 테스트. 분석 서버가 보내는 내부 이벤트의 서비스 인증과 본문 검증, 확인 응답 형식만 본다.
@WebMvcTest(GalleryAnalysisEventController.class)
@Import(GalleryAnalysisEventControllerTest.Properties.class)
class GalleryAnalysisEventControllerTest {
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @TestConfiguration
    static class Properties {
        @Bean
        OrganizeJobProperties organizeJobProperties() {
            return new OrganizeJobProperties(true, null, null, null, null, null, null, null, null, null, null, null,
                null, "event-token");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrganizeEventService eventService;
    @MockitoBean
    TokenProvider tokenProvider;
    @MockitoBean
    AdminTokenProvider adminTokenProvider;
    @MockitoBean
    RoomRepository roomRepository;
    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    private static String body(String state) {
        return """
            {"jobType":"PERSON","jobId":"%s","executionId":"%s","attempt":1,"roomId":10,"state":"%s",
             "progress":{},"result":null,"errorCode":null,"eventId":"ignored"}
            """.formatted(JOB_ID, EXECUTION_ID, state);
    }

    private ResultActions send(String authorization, String body) throws Exception {
        var request = post("/internal/gallery-analysis/events").contentType(MediaType.APPLICATION_JSON).content(body);
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    @Test
    void 서비스_토큰이_맞으면_이벤트를_넘기고_확인_응답을_돌려준다() throws Exception {
        given(eventService.handle(any())).willReturn(new OrganizeEventAck(true, EXECUTION_ID, "RUNNING"));

        send("Bearer event-token", body("RUNNING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
            .andExpect(jsonPath("$.state").value("RUNNING"));
        verify(eventService).handle(any(OrganizeEvent.class));
    }

    @Test
    void 토큰이_없거나_틀리거나_형식이_다르면_401이고_처리하지_않는다() throws Exception {
        send(null, body("RUNNING")).andExpect(status().isUnauthorized());
        send("Bearer wrong", body("RUNNING")).andExpect(status().isUnauthorized());
        send("event-token", body("RUNNING")).andExpect(status().isUnauthorized());

        verify(eventService, never()).handle(any());
    }

    @Test
    void 필수_값이_빠졌으면_400이다() throws Exception {
        send("Bearer event-token", """
            {"jobType":"PERSON","jobId":"%s","roomId":10,"state":"RUNNING"}
            """.formatted(JOB_ID)).andExpect(status().isBadRequest());
    }

    @Test
    void 깨진_본문은_400이다() throws Exception {
        send("Bearer event-token", "{not json").andExpect(status().isBadRequest());
    }
}
