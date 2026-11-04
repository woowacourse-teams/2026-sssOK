package com.sssok.renewal.application.organization.interpret;

import java.util.Objects;

public sealed interface OrganizeInterpretation {
    record ToolSelected(OrganizeTool tool) implements OrganizeInterpretation {
        public ToolSelected {
            Objects.requireNonNull(tool);
        }
    }

    record NeedsClarification() implements OrganizeInterpretation { }

    record Unsupported() implements OrganizeInterpretation { }
}
