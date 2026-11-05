package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 검토 결과 조회의 상태·기한 규칙과 응답 조립을 확인한다.
@OrganizeH2Test
class OrganizeResultServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeResultService resultService;

    private Long host;
    private Long room;
    private List<Long> ids;
    private OrganizeJob job;

    @BeforeEach
    void setUp() {
        host = registeredMember();
        room = room(host);
        ids = jpegs(room, host, 4);
        job = startPerson(room, host);
    }

    private ErrorCode errorOf(Runnable action) {
        return assertThatThrownBy(action::run).isInstanceOf(SssOkException.class).actual() instanceof SssOkException e
            ? e.errorCode() : null;
    }

    @Test
    void 분석이_끝나기_전에는_조회할_수_없다() {
        assertThat(errorOf(() -> resultService.get(room, host, job.publicId())))
            .isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_READY);
    }

    @Test
    void 그룹별_사진과_미분류_개별_오류를_갤러리_항목으로_돌려준다() {
        states.ready(job, objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "person_0001", "mediaIds", ids.subList(0, 2))),
            "unassignedMediaIds", List.of(ids.get(2)),
            "errors", List.of(Map.of("mediaId", ids.get(3), "code", "image_decode_failed")),
            "counts", Map.of(), "pipelineVersion", "v1")));

        OrganizeResultView view = resultService.get(room, host, job.publicId());

        assertThat(view.kind()).isEqualTo(OrganizeKind.PERSON);
        assertThat(view.resultVersion()).isEqualTo(1);
        assertThat(view.removedCount()).isZero();
        assertThat(view.groups()).hasSize(1);
        assertThat(view.groups().get(0).groupId()).isEqualTo("person_0001");
        assertThat(view.groups().get(0).photos()).extracting("mediaId").containsExactly(ids.get(0), ids.get(1));
        assertThat(view.unassigned().photos()).extracting("mediaId").containsExactly(ids.get(2));
        assertThat(view.errors()).extracting(OrganizeResultView.ErrorItem::code).containsExactly("image_decode_failed");
        assertThat(view.smallGroups()).isNull();
    }

    @Test
    void 검토_중_삭제된_사진은_빼고_개수를_알린다() {
        states.ready(job, personResult(ids.subList(0, 2), List.of(ids.get(2))));
        jdbc.update("DELETE FROM stored_file WHERE id = ?", ids.get(0));

        OrganizeResultView view = resultService.get(room, host, job.publicId());

        assertThat(view.removedCount()).isEqualTo(1);
        assertThat(view.groups().get(0).photos()).extracting("mediaId").containsExactly(ids.get(1));
    }

    @Test
    void 사진이_모두_사라진_그룹은_돌려주지_않는다() {
        states.ready(job, personResult(ids.subList(0, 1), List.of(ids.get(1))));
        jdbc.update("DELETE FROM stored_file WHERE id = ?", ids.get(0));

        assertThat(resultService.get(room, host, job.publicId()).groups()).isEmpty();
    }

    @Test
    void 유사_사진은_소그룹을_함께_돌려준다() {
        Long other = registeredMember();
        Long otherRoom = room(other);
        List<Long> five = jpegs(otherRoom, other, 5);
        OrganizeJob similar = startService.startByKind(otherRoom, other, OrganizeKind.SIMILAR, key());
        states.ready(similar, objectMapper.valueToTree(Map.of("resultVersion", 1, "pipelineVersion", "v1",
            "groups", List.of(Map.of("groupId", "similar_0001", "mediaIds", five.subList(0, 3))),
            "smallGroupMediaIds", List.of(five.subList(3, 5)), "unassignedMediaIds", List.of(), "errors", List.of(),
            "counts", Map.of())));

        OrganizeResultView view = resultService.get(otherRoom, other, similar.publicId());

        assertThat(view.smallGroups()).hasSize(1);
        assertThat(view.smallGroups().get(0).groupId()).isNull();
        assertThat(view.smallGroups().get(0).photos()).hasSize(2);
    }

    @Test
    void 검토_기한이_지나면_결과_본문이_남아_있어도_만료다() {
        states.ready(job);
        clock.advance(Duration.ofMinutes(31));

        assertThat(errorOf(() -> resultService.get(room, host, job.publicId())))
            .isEqualTo(ErrorCode.ORGANIZE_REVIEW_EXPIRED);
        assertThat(reload(job).hasResult()).isTrue();
    }

    @Test
    void 반영된_결과는_보관_기한까지_조회할_수_있고_그_뒤는_410이다() {
        states.ready(job);
        repository.markJobApplied(job.id(), clock.instant());

        assertThat(resultService.get(room, host, job.publicId()).groups()).hasSize(1);

        clock.advance(Duration.ofDays(3).plusMinutes(1));
        assertThat(errorOf(() -> resultService.get(room, host, job.publicId())))
            .isEqualTo(ErrorCode.ORGANIZE_RESULT_EXPIRED);
    }

    @Test
    void 요청자가_아니면_404다() {
        states.ready(job);
        Long guest = registeredMember();

        assertThat(errorOf(() -> resultService.get(room, guest, job.publicId())))
            .isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_FOUND);
    }
}
