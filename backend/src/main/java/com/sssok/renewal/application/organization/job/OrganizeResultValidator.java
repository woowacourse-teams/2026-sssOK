package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// Python 이 보낸 완료 결과를 저장하기 전에 검증한다. 모든 mediaId 가 저장된 분석 대상에 속해야 한다.
// 인물(PERSON)은 같은 사진이 여러 그룹에 있을 수 있다. 유사 사진(SIMILAR)은 그룹 3장 이상, 서로 겹치지 않고,
// 소그룹·미분류·오류까지 합쳐 입력 전체를 정확히 한 번씩 나눈다.
@Component
public class OrganizeResultValidator {
    private static final int MIN_SIMILAR_GROUP_SIZE = 3;
    private static final int SMALL_GROUP_SIZE = 2;
    private static final int SIMILAR_RESULT_VERSION = 1;

    public record Validation(boolean valid, int version, String reason) {
        static Validation ok(int version) {
            return new Validation(true, version, null);
        }

        static Validation invalid(String reason) {
            return new Validation(false, 0, reason);
        }
    }

    public Validation validate(OrganizeKind kind, JsonNode result, Set<Long> targetIds) {
        try {
            return Validation.ok(check(kind, result, targetIds));
        } catch (InvalidResult invalid) {
            return Validation.invalid(invalid.getMessage());
        }
    }

    // 잘못된 곳을 만나면 바로 InvalidResult 로 빠져나온다. 통과하면 결과 버전을 돌려준다.
    private int check(OrganizeKind kind, JsonNode result, Set<Long> targetIds) {
        if (result == null || !result.isObject()) {
            throw new InvalidResult("결과가 객체가 아닙니다");
        }
        int version = result.path("resultVersion").asInt(1);
        if (kind == OrganizeKind.SIMILAR && version != SIMILAR_RESULT_VERSION) {
            throw new InvalidResult("지원하지 않는 결과 버전입니다");
        }
        List<List<Long>> groups = readGroups(result.path("groups"), targetIds);
        List<Long> unassigned = readIds("미분류", result.path("unassignedMediaIds"), targetIds);
        List<Long> errors = readErrorIds(result.path("errors"), targetIds);
        requireDistinctGroupIds(result.path("groups"));
        if (kind == OrganizeKind.SIMILAR) {
            List<List<Long>> small = readSmallGroups(result.path("smallGroupMediaIds"), targetIds);
            requireExactPartition(groups, small, unassigned, errors, targetIds);
        }
        return version;
    }

    private void requireExactPartition(List<List<Long>> groups, List<List<Long>> small, List<Long> unassigned,
                                       List<Long> errors, Set<Long> targetIds) {
        if (groups.stream().anyMatch(group -> group.size() < MIN_SIMILAR_GROUP_SIZE)) {
            throw new InvalidResult("유사 사진 그룹은 3장 이상이어야 합니다");
        }
        List<Long> all = new ArrayList<>();
        groups.forEach(all::addAll);
        small.forEach(all::addAll);
        all.addAll(unassigned);
        all.addAll(errors);
        if (all.size() != targetIds.size() || !new HashSet<>(all).equals(targetIds)) {
            throw new InvalidResult("입력 사진을 겹침 없이 정확히 나누지 않았습니다");
        }
    }

    private void requireDistinctGroupIds(JsonNode groups) {
        Set<String> seen = new HashSet<>();
        for (JsonNode group : groups) {
            JsonNode id = group.path("groupId");
            if (!id.isTextual() || id.asText().isBlank() || !seen.add(id.asText())) {
                throw new InvalidResult("그룹 ID가 비었거나 중복됩니다");
            }
        }
    }

    private List<List<Long>> readGroups(JsonNode groups, Set<Long> targetIds) {
        requireArray("그룹", groups);
        List<List<Long>> result = new ArrayList<>();
        for (JsonNode group : groups) {
            if (!group.isObject()) {
                throw new InvalidResult("그룹의 형식이 올바르지 않습니다");
            }
            List<Long> ids = readIds("그룹", group.path("mediaIds"), targetIds);
            if (ids.isEmpty()) {
                throw new InvalidResult("사진이 없는 그룹이 있습니다");
            }
            result.add(ids);
        }
        return result;
    }

    private List<List<Long>> readSmallGroups(JsonNode groups, Set<Long> targetIds) {
        requireArray("소그룹", groups);
        List<List<Long>> result = new ArrayList<>();
        for (JsonNode group : groups) {
            List<Long> ids = readIds("소그룹", group, targetIds);
            if (ids.size() != SMALL_GROUP_SIZE) {
                throw new InvalidResult("소그룹은 2장이어야 합니다");
            }
            result.add(ids);
        }
        return result;
    }

    private List<Long> readErrorIds(JsonNode errors, Set<Long> targetIds) {
        requireArray("오류", errors);
        List<Long> ids = new ArrayList<>();
        for (JsonNode error : errors) {
            JsonNode mediaId = error.path("mediaId");
            if (!error.isObject() || !mediaId.isIntegralNumber() || !targetIds.contains(mediaId.asLong())
                || !error.path("code").isTextual()) {
                throw new InvalidResult("오류 항목의 형식이나 사진 범위가 올바르지 않습니다");
            }
            ids.add(mediaId.asLong());
        }
        return ids;
    }

    // 한 목록 안에서 같은 사진이 두 번 나오면 잘못된 결과다.
    private List<Long> readIds(String label, JsonNode array, Set<Long> targetIds) {
        requireArray(label, array);
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode node : array) {
            if (!node.isIntegralNumber() || !targetIds.contains(node.asLong()) || !seen.add(node.asLong())) {
                throw new InvalidResult(label + "의 사진 ID가 숫자가 아니거나 분석 대상이 아니거나 중복됩니다");
            }
            ids.add(node.asLong());
        }
        return ids;
    }

    private void requireArray(String label, JsonNode node) {
        if (!node.isArray()) {
            throw new InvalidResult(label + " 목록이 배열이 아닙니다");
        }
    }

    private static class InvalidResult extends RuntimeException {
        InvalidResult(String reason) {
            super(reason, null, false, false);
        }
    }
}
