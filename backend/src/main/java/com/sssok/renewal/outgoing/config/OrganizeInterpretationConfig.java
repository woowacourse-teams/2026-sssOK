package com.sssok.renewal.outgoing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.InterpretOrganizeRequestService;
import com.sssok.renewal.application.organization.interpret.OrganizeRequestInterpreter;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.ai.OpenAiOrganizeRequestInterpreter;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
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
    @Bean
    public InterpretOrganizeRequestService interpretOrganizeRequestService(McpOrganizeToolCatalog catalog,
                                                                         OrganizeRequestInterpreter interpreter) {
        return new InterpretOrganizeRequestService(catalog, interpreter);
    }

    @Bean
    public McpOrganizeToolCatalog organizeToolCatalog(OrganizeMcpProperties properties) {
        return new McpOrganizeToolCatalog(properties.serverId(), properties.exposedTools(),
            () -> createMcpClient(properties));
    }

    @Bean
    public OrganizeRequestInterpreter organizeRequestInterpreter(OrganizeInterpretationProperties properties,
                                                               ObjectMapper mapper) {
        OpenAiChatModel model = createChatModel(properties);
        return new OpenAiOrganizeRequestInterpreter(model, mapper);
    }

    private OpenAiChatModel createChatModel(OrganizeInterpretationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(properties.apiKey())
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(properties.model()).maxCompletionTokens(512).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
    }

    private McpSyncClient createMcpClient(OrganizeMcpProperties properties) {
        String origin = properties.url().getScheme() + "://" + properties.url().getRawAuthority();
        String path = properties.url().getRawPath();
        String endpoint = path == null || path.isEmpty() ? "/mcp" : path;
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(origin)
            .endpoint(endpoint)
            .connectTimeout(properties.timeout())
            .openConnectionOnStartup(false)
            .resumableStreams(false)
            .customizeRequest(request -> request.header("Authorization", "Bearer " + properties.token()))
            .build();
        return McpClient.sync(transport).requestTimeout(properties.timeout()).build();
    }
}
