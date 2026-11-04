package com.sssok.renewal.application.organization.interpret;

import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InterpretOrganizeRequestService {
    private final McpOrganizeToolCatalog catalog;
    private final OrganizeRequestInterpreter interpreter;

    public OrganizeInterpretation interpret(String text) {
        OrganizeRequestInterpreter.validateText(text);
        List<OrganizeTool> availableTools = catalog.fetchTools();
        return interpreter.interpret(text, availableTools);
    }
}
