package com.sssok.renewal.application.organization.execute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizeExecutionRequestTest {
    private static final OrganizeTool TOOL = new OrganizeTool("gallery", "group_photos_by_person", "사람별로 묶는다");

    @Test
    void 사진이_없으면_호출_전에_거절한다() {
        assertThatThrownBy(() -> request(1, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사진이_100장을_넘으면_호출_전에_거절한다() {
        assertThat(request(1, photos(100)).photos()).hasSize(100);
        assertThatThrownBy(() -> request(1, photos(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_사진이_중복되면_거절한다() {
        assertThatThrownBy(() -> request(1, List.of(photo(1), photo(1)))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 실행_회차는_양수여야_한다(int attempt) {
        assertThatThrownBy(() -> request(attempt, photos(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 서명_URL은_문자열_표현에_남기지_않는다() {
        assertThat(request(1, photos(1)).toString()).doesNotContain("https://r2.test");
    }

    private OrganizeExecutionRequest request(int attempt, List<AnalysisPhoto> photos) {
        return new OrganizeExecutionRequest(TOOL, 7L, UUID.randomUUID(), UUID.randomUUID(), attempt, photos);
    }

    private List<AnalysisPhoto> photos(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(this::photo).toList();
    }

    private AnalysisPhoto photo(long mediaId) {
        return new AnalysisPhoto(mediaId, "https://r2.test/" + mediaId + "?X-Amz-Signature=secret", "jpg");
    }
}
