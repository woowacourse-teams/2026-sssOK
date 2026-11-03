package com.sssok.renewal.outgoing.ai;

import static com.sssok.renewal.outgoing.ai.OrganizeToolSchemas.serverManagedInputs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sssok.renewal.application.organization.interpret.InterpretationException;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class McpOrganizeToolCatalogTest {
    private final McpSyncClient client = mock(McpSyncClient.class);
    private final McpOrganizeToolCatalog catalog =
        new McpOrganizeToolCatalog("gallery", Set.of("new_analysis"), () -> client);

    @Test
    void 공개_설정한_새_도구의_이름과_설명을_MCP에서_가져온다() {
        when(client.listTools(null)).thenReturn(new ListToolsResult(
            List.of(tool("new_analysis", serverManagedInputs()), tool("get_gallery_job", serverManagedInputs())), null));
        assertThat(catalog.fetchTools()).containsExactly(new OrganizeTool("gallery", "new_analysis", "새 분석 설명"));
        verify(client).initialize();
        verify(client).close();
    }

    @Test
    void 다음_페이지의_도구도_발견한다() {
        when(client.listTools(null)).thenReturn(new ListToolsResult(List.of(), "next"));
        when(client.listTools("next")).thenReturn(new ListToolsResult(List.of(tool("new_analysis", serverManagedInputs())), null));
        assertThat(catalog.fetchTools()).hasSize(1);
    }

    @Test
    void 다음_조회에서_삭제된_도구를_다시_노출하지_않는다() {
        when(client.listTools(null))
            .thenReturn(new ListToolsResult(List.of(tool("new_analysis", serverManagedInputs())), null))
            .thenReturn(new ListToolsResult(List.of(), null));
        assertThat(catalog.fetchTools()).hasSize(1);
        assertThat(catalog.fetchTools()).isEmpty();
    }

    @Test
    void 다른_입력_계약은_인자를_임의로_버리지_않고_노출하지_않는다() {
        JsonSchema otherInput = new JsonSchema("object", Map.of("threshold", Map.of("type", "number")),
            List.of("threshold"), false, null, null);
        when(client.listTools(null)).thenReturn(new ListToolsResult(List.of(tool("new_analysis", otherInput)), null));
        assertThat(catalog.fetchTools()).isEmpty();
    }

    @Test
    void 커서가_반복되면_무한_조회하지_않는다() {
        when(client.listTools(null)).thenReturn(new ListToolsResult(List.of(), "next"));
        when(client.listTools("next")).thenReturn(new ListToolsResult(List.of(), "next"));
        assertThatThrownBy(catalog::fetchTools).isInstanceOf(InterpretationException.class);
        verify(client).close();
    }

    @Test
    void 조회_실패는_빈_목록으로_위장하지_않는다() {
        when(client.listTools(null)).thenThrow(new IllegalStateException("private transport details"));
        assertThatThrownBy(catalog::fetchTools)
            .isInstanceOfSatisfying(InterpretationException.class,
                failure -> assertThat(failure.reason()).isEqualTo(InterpretationException.Reason.CATALOG_UNAVAILABLE))
            .hasMessageNotContaining("private transport details");
        verify(client).close();
    }

    private Tool tool(String name, JsonSchema schema) {
        return Tool.builder().name(name).description("새 분석 설명").inputSchema(schema).build();
    }
}
