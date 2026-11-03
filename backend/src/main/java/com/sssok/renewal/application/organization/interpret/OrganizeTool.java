package com.sssok.renewal.application.organization.interpret;

// 현재는 방 전체 사진을 서버가 전달하는 분석 도구만 공개한다. 모델이 채울 인자는 없다.
public record OrganizeTool(String serverId, String name, String description) {
    public OrganizeTool {
        if (serverId == null || serverId.isBlank() || name == null || name.isBlank()
            || description == null || description.isBlank()) {
            throw new IllegalArgumentException("정리 도구의 서버, 이름, 설명이 필요합니다");
        }
    }
}
