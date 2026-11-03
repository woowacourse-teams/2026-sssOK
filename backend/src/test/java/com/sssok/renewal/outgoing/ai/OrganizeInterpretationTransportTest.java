package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.OrganizeInterpretation;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrganizeInterpretationTransportTest {
    @Test
    void 실제_Spring_AI_요청은_허용_도구만_보내고_선택_후_추가_호출하지_않는다() {
        ObjectMapper mapper = new ObjectMapper();
        RestClient.Builder restClient = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(request -> {
                JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                assertThat(body.path("model").asText()).isEqualTo("test-model");
                assertThat(body.path("tool_choice").asText()).isEqualTo("required");
                assertThat(body.path("parallel_tool_calls").asBoolean()).isFalse();
                assertThat(body.path("store").asBoolean()).isFalse();
                assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(512);
                assertThat(body.path("messages")).hasSize(2);
                assertThat(body.at("/messages/1/content").asText()).isEqualTo("사람별로 모아줘");
                assertThat(body.path("tools")).hasSize(3);
                for (JsonNode tool : body.path("tools")) {
                    assertThat(tool.at("/function/strict").asBoolean()).isTrue();
                    assertThat(tool.at("/function/parameters/properties").size()).isZero();
                    assertThat(tool.at("/function/parameters/additionalProperties").asBoolean()).isFalse();
                }
            })
            .andRespond(withSuccess("""
                {"id":"test","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{
                   "role":"assistant","content":null,"tool_calls":[{
                     "id":"selection","type":"function",
                     "function":{"name":"analysis_0","arguments":"{}"}
                   }]
                 }}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiChatModel model = OpenAiChatModel.builder()
            .openAiApi(OpenAiApi.builder().apiKey("test-key").restClientBuilder(restClient).build())
            .defaultOptions(OpenAiChatOptions.builder().model("test-model").maxCompletionTokens(512).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();

        OrganizeTool discovered = new OrganizeTool("gallery", "new_analysis", "새 분석 도구");
        assertThat(new OpenAiOrganizeRequestInterpreter(model, mapper).interpret("사람별로 모아줘", List.of(discovered)))
            .isEqualTo(new OrganizeInterpretation.ToolSelected(discovered));
        server.verify();
    }
}
