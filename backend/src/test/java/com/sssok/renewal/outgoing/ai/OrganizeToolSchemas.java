package com.sssok.renewal.outgoing.ai;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import java.util.List;
import java.util.Map;

final class OrganizeToolSchemas {
    private OrganizeToolSchemas() {
    }

    static JsonSchema serverManagedInputs() {
        return new JsonSchema("object", Map.of(
            "room_id", Map.of("type", "integer"),
            "job_id", Map.of("type", "string"),
            "request_id", Map.of("type", "string"),
            "attempt", Map.of("type", "integer"),
            "photos", Map.of("type", "array", "items", Map.of("type", "object"))),
            List.of("room_id", "job_id", "request_id", "attempt", "photos"), false, null, null);
    }
}
