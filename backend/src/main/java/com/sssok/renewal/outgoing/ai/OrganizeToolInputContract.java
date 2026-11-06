package com.sssok.renewal.outgoing.ai;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import java.util.Map;
import java.util.Set;

final class OrganizeToolInputContract {
    private static final Map<String, String> SERVER_FIELD_TYPES = Map.of(
        "room_id", "integer",
        "job_id", "string",
        "request_id", "string",
        "attempt", "integer",
        "photos", "array");

    private OrganizeToolInputContract() {
    }

    static boolean accepts(JsonSchema schema) {
        return schema != null
            && "object".equals(schema.type())
            && declaresOnlyServerFields(schema)
            && requiresAllServerFields(schema)
            && serverFieldTypesMatch(schema);
    }

    private static boolean declaresOnlyServerFields(JsonSchema schema) {
        return schema.properties() != null && schema.properties().keySet().equals(SERVER_FIELD_TYPES.keySet());
    }

    private static boolean requiresAllServerFields(JsonSchema schema) {
        return schema.required() != null && Set.copyOf(schema.required()).equals(SERVER_FIELD_TYPES.keySet());
    }

    private static boolean serverFieldTypesMatch(JsonSchema schema) {
        return SERVER_FIELD_TYPES.entrySet().stream().allMatch(field ->
            schema.properties().get(field.getKey()) instanceof Map<?, ?> definition
                && field.getValue().equals(definition.get("type")));
    }
}
