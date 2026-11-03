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
            .andRespond(withSuccess(SELECTION_RESPONSE, MediaType.APPLICATION_JSON));
        OpenAiChatModel model = chatModel(restClient, OpenAiChatOptions.builder());

        OrganizeTool discovered = new OrganizeTool("gallery", "new_analysis", "새 분석 도구");
        assertThat(new OpenAiOrganizeRequestInterpreter(model, mapper).interpret("사람별로 모아줘", List.of(discovered)))
            .isEqualTo(new OrganizeInterpretation.ToolSelected(discovered));
        server.verify();
    }

    @Test
    void 설정한_추론_강도는_요청에_포함되고_설정하지_않으면_보내지_않는다() {
        ObjectMapper mapper = new ObjectMapper();
        OrganizeTool discovered = new OrganizeTool("gallery", "new_analysis", "새 분석 도구");
        for (String effort : new String[] {"none", null}) {
            RestClient.Builder restClient = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();
            server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(request -> {
                    JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    if (effort == null) {
                        assertThat(body.has("reasoning_effort")).isFalse();
                    } else {
                        assertThat(body.path("reasoning_effort").asText()).isEqualTo(effort);
                    }
                })
                .andRespond(withSuccess(SELECTION_RESPONSE, MediaType.APPLICATION_JSON));
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
            if (effort != null) {
                options.reasoningEffort(effort);
            }

            new OpenAiOrganizeRequestInterpreter(chatModel(restClient, options), mapper)
                .interpret("사람별로 모아줘", List.of(discovered));
            server.verify();
        }
    }

    private OpenAiChatModel chatModel(RestClient.Builder restClient, OpenAiChatOptions.Builder options) {
        return OpenAiChatModel.builder()
            .openAiApi(OpenAiApi.builder().apiKey("test-key").restClientBuilder(restClient).build())
            .defaultOptions(options.model("test-model").maxCompletionTokens(512).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
    }

    private static final String SELECTION_RESPONSE = """
        {"id":"test","object":"chat.completion","created":1,"model":"test-model",
         "choices":[{"index":0,"finish_reason":"tool_calls","message":{
           "role":"assistant","content":null,"tool_calls":[{
             "id":"selection","type":"function",
             "function":{"name":"analysis_0","arguments":"{}"}
           }]
         }}]}
        """;
}
