package com.sssok.renewal.outgoing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.InterpretOrganizeRequestService;
import com.sssok.renewal.application.organization.interpret.OrganizeRequestInterpreter;
import com.sssok.renewal.outgoing.ai.McpAuthenticationCheck;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolExecutor;
import com.sssok.renewal.outgoing.ai.OpenAiOrganizeRequestInterpreter;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
// MCP 연결(도구 목록·실행)은 사진 정리 작업이나 문장 해석 중 하나라도 켜지면 필요하다.
// 모델 해석(OpenAI)은 문장 해석을 켰을 때만 만든다. 버튼 시작은 모델 없이 MCP 만 쓴다.
@ConditionalOnExpression("${gallery.organization.interpretation.enabled:false} or ${gallery.organization.job.enabled:false}")
@EnableConfigurationProperties({OrganizeInterpretationProperties.class, OrganizeMcpProperties.class})
public class OrganizeInterpretationConfig {
    private static final int MAX_COMPLETION_TOKENS = 512;
    private static final String DEFAULT_MCP_ENDPOINT = "/mcp";

    @Bean
    @ConditionalOnProperty(name = "gallery.organization.interpretation.enabled", havingValue = "true")
    public InterpretOrganizeRequestService interpretOrganizeRequestService(McpOrganizeToolCatalog catalog,
                                                                         OrganizeRequestInterpreter interpreter) {
        return new InterpretOrganizeRequestService(catalog, interpreter);
    }

    @Bean
    public McpOrganizeToolCatalog organizeToolCatalog(OrganizeMcpProperties properties) {
        properties.requireValid();
        return new McpOrganizeToolCatalog(properties.serverId(), properties.exposedTools(),
            () -> createMcpClient(properties));
    }

    @Bean
    public McpOrganizeToolExecutor organizeToolExecutor(McpOrganizeToolCatalog catalog,
                                                        OrganizeMcpProperties properties) {
        properties.requireValid();
        var authenticationCheck = new McpAuthenticationCheck(properties.url(), properties.token(), properties.timeout());
        return new McpOrganizeToolExecutor(catalog, () -> createMcpClient(properties), authenticationCheck);
    }

    @Bean
    @ConditionalOnProperty(name = "gallery.organization.interpretation.enabled", havingValue = "true")
    public OrganizeRequestInterpreter organizeRequestInterpreter(OrganizeInterpretationProperties properties,
                                                               ObjectMapper mapper) {
        properties.requireValid();
        return new OpenAiOrganizeRequestInterpreter(createChatModel(properties), mapper);
    }

    private OpenAiChatModel createChatModel(OrganizeInterpretationProperties properties) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
            .model(properties.model())
            .maxCompletionTokens(MAX_COMPLETION_TOKENS);
        if (properties.reasoningEffort() != null) {
            options.reasoningEffort(properties.reasoningEffort());
        }
        return OpenAiChatModel.builder()
            .openAiApi(createOpenAiApi(properties))
            .defaultOptions(options.build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
    }

    private OpenAiApi createOpenAiApi(OrganizeInterpretationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return OpenAiApi.builder()
            .apiKey(properties.apiKey())
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();
    }

    private McpSyncClient createMcpClient(OrganizeMcpProperties properties) {
        URI url = properties.url();
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
            .builder(url.getScheme() + "://" + url.getRawAuthority())
            .endpoint(endpointOf(url))
            .connectTimeout(properties.timeout())
            .openConnectionOnStartup(false)
            .resumableStreams(false)
            .customizeRequest(request -> request.header("Authorization", "Bearer " + properties.token()))
            .build();
        return McpClient.sync(transport).requestTimeout(properties.timeout()).build();
    }

    private String endpointOf(URI url) {
        String path = url.getRawPath();
        return path == null || path.isEmpty() ? DEFAULT_MCP_ENDPOINT : path;
    }
}
