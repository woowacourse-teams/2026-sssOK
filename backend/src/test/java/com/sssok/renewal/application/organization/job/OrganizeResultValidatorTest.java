package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrganizeResultValidatorTest {
    private static final Set<Long> TARGET = Set.of(1L, 2L, 3L, 4L, 5L);

    private final ObjectMapper mapper = new ObjectMapper();
    private final OrganizeResultValidator validator = new OrganizeResultValidator();

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    @Test
    void 인물_결과는_같은_사진이_여러_그룹에_있어도_통과한다() throws Exception {
        JsonNode result = json("""
            {"groups":[{"groupId":"a","mediaIds":[1,2]},{"groupId":"b","mediaIds":[2,3]}],
             "unassignedMediaIds":[4],"errors":[]}
            """);

        var validation = validator.validate(OrganizeKind.PERSON, result, TARGET);

        assertThat(validation.valid()).isTrue();
        assertThat(validation.version()).isEqualTo(1);
    }

    @Test
    void 인물_결과는_사람이_없어도_통과하고_비었다고_표시한다() throws Exception {
        var validation = validator.validate(OrganizeKind.PERSON,
            json("{\"groups\":[],\"unassignedMediaIds\":[1,2],\"errors\":[]}"), TARGET);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void 대상이_아닌_사진_ID는_거절한다() throws Exception {
        var validation = validator.validate(OrganizeKind.PERSON,
            json("{\"groups\":[{\"groupId\":\"a\",\"mediaIds\":[1,99]}],\"unassignedMediaIds\":[],\"errors\":[]}"),
            TARGET);

        assertThat(validation.valid()).isFalse();
    }

    @Test
    void 그룹_ID가_비었거나_중복이면_거절한다() throws Exception {
        assertThat(validator.validate(OrganizeKind.PERSON, json("""
            {"groups":[{"groupId":"a","mediaIds":[1]},{"groupId":"a","mediaIds":[2]}],
             "unassignedMediaIds":[],"errors":[]}
            """), TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.PERSON, json("""
            {"groups":[{"mediaIds":[1]}],"unassignedMediaIds":[],"errors":[]}
            """), TARGET).valid()).isFalse();
    }

    @Test
    void 한_목록에서_같은_사진이_두_번_나오면_거절한다() throws Exception {
        var validation = validator.validate(OrganizeKind.PERSON,
            json("{\"groups\":[{\"groupId\":\"a\",\"mediaIds\":[1,1]}],\"unassignedMediaIds\":[],\"errors\":[]}"),
            TARGET);

        assertThat(validation.valid()).isFalse();
    }

    @Test
    void 오류_항목의_사진_ID가_대상이_아니거나_없으면_거절한다() throws Exception {
        assertThat(validator.validate(OrganizeKind.PERSON,
            json("{\"groups\":[],\"unassignedMediaIds\":[],\"errors\":[{\"mediaId\":99,\"code\":\"x\"}]}"),
            TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.PERSON,
            json("{\"groups\":[],\"unassignedMediaIds\":[],\"errors\":[{\"mediaId\":null,\"code\":\"x\"}]}"),
            TARGET).valid()).isFalse();
    }

    @Test
    void 필수_배열이_없으면_거절한다() throws Exception {
        assertThat(validator.validate(OrganizeKind.PERSON, json("{\"groups\":[]}"), TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.PERSON, json("[]"), TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.PERSON, null, TARGET).valid()).isFalse();
    }

    @Test
    void 유사_사진은_입력_전체를_겹침_없이_정확히_나누어야_통과한다() throws Exception {
        JsonNode result = json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2,3]}],
             "smallGroupMediaIds":[[4,5]],"unassignedMediaIds":[],"errors":[]}
            """);

        assertThat(validator.validate(OrganizeKind.SIMILAR, result, TARGET).valid()).isTrue();
    }

    @Test
    void 유사_사진_오류_사진도_분할에_포함한다() throws Exception {
        JsonNode result = json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2,3]}],
             "smallGroupMediaIds":[],"unassignedMediaIds":[4],"errors":[{"mediaId":5,"code":"image_decode_failed"}]}
            """);

        assertThat(validator.validate(OrganizeKind.SIMILAR, result, TARGET).valid()).isTrue();
    }

    @Test
    void 유사_사진_그룹이_3장_미만이면_거절한다() throws Exception {
        JsonNode result = json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2]}],
             "smallGroupMediaIds":[],"unassignedMediaIds":[3,4,5],"errors":[]}
            """);

        assertThat(validator.validate(OrganizeKind.SIMILAR, result, TARGET).valid()).isFalse();
    }

    @Test
    void 유사_사진_그룹끼리_겹치거나_사진이_빠지면_거절한다() throws Exception {
        assertThat(validator.validate(OrganizeKind.SIMILAR, json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2,3]},{"groupId":"s2","mediaIds":[3,4,5]}],
             "smallGroupMediaIds":[],"unassignedMediaIds":[],"errors":[]}
            """), TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.SIMILAR, json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2,3]}],
             "smallGroupMediaIds":[],"unassignedMediaIds":[4],"errors":[]}
            """), TARGET).valid()).isFalse();
    }

    @Test
    void 유사_사진_소그룹은_2장이어야_하고_결과_버전은_1이어야_한다() throws Exception {
        assertThat(validator.validate(OrganizeKind.SIMILAR, json("""
            {"resultVersion":1,"groups":[{"groupId":"s1","mediaIds":[1,2,3]}],
             "smallGroupMediaIds":[[4]],"unassignedMediaIds":[5],"errors":[]}
            """), TARGET).valid()).isFalse();
        assertThat(validator.validate(OrganizeKind.SIMILAR, json("""
            {"resultVersion":2,"groups":[{"groupId":"s1","mediaIds":[1,2,3]}],
             "smallGroupMediaIds":[[4,5]],"unassignedMediaIds":[],"errors":[]}
            """), TARGET).valid()).isFalse();
    }
}
