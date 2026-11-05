package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrganizeResultTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SIMILAR = """
        {"resultVersion":1,"extra":"무시",
         "groups":[{"groupId":"similar_0001","mediaIds":[1,2,3]}],
         "smallGroupMediaIds":[[4,5]],
         "unassignedMediaIds":[6],
         "errors":[{"mediaId":7,"code":"DECODE_FAILED"}]}
        """;

    @Test
    void 저장된_JSON을_그룹_소그룹_미분류_오류로_읽는다() {
        OrganizeResult result = OrganizeResult.parse(MAPPER, SIMILAR);

        assertThat(result.groups()).hasSize(1);
        assertThat(result.smallGroupMediaIds()).containsExactly(List.of(4L, 5L));
        assertThat(result.unassignedMediaIds()).containsExactly(6L);
        assertThat(result.errors()).containsExactly(new OrganizeResult.ErrorItem(7L, "DECODE_FAILED"));
    }

    @Test
    void 없는_목록은_빈_목록으로_읽는다() {
        OrganizeResult result = OrganizeResult.parse(MAPPER, "{\"groups\":[{\"groupId\":\"person_0001\",\"mediaIds\":[1]}]}");

        assertThat(result.smallGroupMediaIds()).isEmpty();
        assertThat(result.unassignedMediaIds()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void 사용자에게_보여_주는_사진은_그룹_소그룹_미분류이고_오류는_제외한다() {
        OrganizeResult result = OrganizeResult.parse(MAPPER, SIMILAR);

        assertThat(result.referencedMediaIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void 일반_그룹만_groupId로_찾는다() {
        OrganizeResult result = OrganizeResult.parse(MAPPER, SIMILAR);

        assertThat(result.findGroup("similar_0001")).isPresent();
        assertThat(result.findGroup("similar_0001").orElseThrow().containsAll(List.of(1L, 3L))).isTrue();
        assertThat(result.findGroup("similar_0001").orElseThrow().containsAll(List.of(1L, 4L))).isFalse();
        assertThat(result.findGroup("small_0001")).isEmpty();
    }

    @Test
    void 읽을_수_없는_JSON이면_예외다() {
        assertThatThrownBy(() -> OrganizeResult.parse(MAPPER, "{"))
            .isInstanceOf(IllegalStateException.class);
    }
}
