package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 검증을 통과해 저장된 분석 결과. 저장된 JSON 을 한 번 읽어 그룹 단위로 다룬다.
// smallGroupMediaIds 는 유사 사진(SIMILAR)에만 있다. 없는 목록은 빈 목록으로 읽는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizeResult(List<Group> groups, List<List<Long>> smallGroupMediaIds, List<Long> unassignedMediaIds,
                             List<ErrorItem> errors) {

    public OrganizeResult {
        groups = groups == null ? List.of() : groups;
        smallGroupMediaIds = smallGroupMediaIds == null ? List.of() : smallGroupMediaIds;
        unassignedMediaIds = unassignedMediaIds == null ? List.of() : unassignedMediaIds;
        errors = errors == null ? List.of() : errors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Group(String groupId, List<Long> mediaIds) {
        public Group {
            mediaIds = mediaIds == null ? List.of() : mediaIds;
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorItem(long mediaId, String code) {
    }

    public static OrganizeResult parse(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, OrganizeResult.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("저장된 사진 정리 결과를 읽을 수 없습니다", failure);
        }
    }

    // 사용자에게 보여 주는 사진(그룹·소그룹·미분류). 오류 항목은 사진 목록에 넣지 않는다.
    public Set<Long> referencedMediaIds() {
        Set<Long> ids = new LinkedHashSet<>();
        groups.forEach(group -> ids.addAll(group.mediaIds()));
        smallGroupMediaIds.forEach(ids::addAll);
        ids.addAll(unassignedMediaIds);
        return ids;
    }
}
