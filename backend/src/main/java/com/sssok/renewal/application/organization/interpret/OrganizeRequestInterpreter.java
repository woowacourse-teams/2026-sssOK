package com.sssok.renewal.application.organization.interpret;

import java.util.List;

public interface OrganizeRequestInterpreter {
    int MAX_TEXT_LENGTH = 1000;

    OrganizeInterpretation interpret(String text, List<OrganizeTool> tools);

    static void validateText(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("사진 정리 요청은 1~" + MAX_TEXT_LENGTH + "자여야 합니다");
        }
    }
}
