package com.sssok.renewal.application.organization.interpret;

public record OrganizeTool(String serverId, String name, String description) {
    public OrganizeTool {
        if (isBlank(serverId) || isBlank(name) || isBlank(description)) {
            throw new IllegalArgumentException("정리 도구의 서버, 이름, 설명이 필요합니다");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
