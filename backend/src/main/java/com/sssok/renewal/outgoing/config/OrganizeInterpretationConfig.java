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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "gallery.organization.interpretation.enabled", havingValue = "true")
@EnableConfigurationProperties({OrganizeInterpretationProperties.class, OrganizeMcpProperties.class})
public class OrganizeInterpretationConfig {
    private static final int MAX_COMPLETION_TOKENS = 512;
    private static final String DEFAULT_MCP_ENDPOINT = "/mcp";

    @Bean
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
