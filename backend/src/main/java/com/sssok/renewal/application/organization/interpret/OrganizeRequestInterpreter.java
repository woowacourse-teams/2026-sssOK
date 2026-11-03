package com.sssok.renewal.application.organization.interpret;

import java.util.List;

public interface OrganizeRequestInterpreter {
    OrganizeInterpretation interpret(String text, List<OrganizeTool> tools);

    static void validateText(String text) {
        if (text == null || text.isBlank() || text.length() > 1000) {
            throw new IllegalArgumentException("사진 정리 요청은 1~1000자여야 합니다");
        }
    }
}
