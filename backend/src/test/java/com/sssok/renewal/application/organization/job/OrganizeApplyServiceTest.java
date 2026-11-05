package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.application.organization.job.OrganizeApplyService.GroupRequest;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 폴더 반영이 규칙 위반으로 거절되는 경로를 확인한다.
// 폴더 담기 쿼리가 PostgreSQL 전용이라 성공 경로는 OrganizeApplyServicePostgresTest 에 있다.
@OrganizeH2Test
class OrganizeApplyServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeApplyService applyService;
    @Autowired
    CreateFolderService createFolderService;

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
        // 앞 3장이 person_0001, 마지막 1장이 미분류다.
        states.ready(job, personResult(ids.subList(0, 3), List.of(ids.get(3))));
    }

    private ErrorCode errorOf(Runnable action) {
        SssOkException e = org.junit.jupiter.api.Assertions.assertThrows(SssOkException.class, action::run);
        return e.errorCode();
    }

    private Long folders() {
        return jdbc.queryForObject("SELECT count(*) FROM folder WHERE room_id = ?", Long.class, room);
    }

    private Long links() {
        return jdbc.queryForObject(
            "SELECT count(*) FROM folder_media WHERE folder_id IN (SELECT id FROM folder WHERE room_id = ?)",
            Long.class, room);
    }

    private List<GroupRequest> person(String folderName, List<Long> mediaIds) {
        return List.of(new GroupRequest("person_0001", folderName, mediaIds));
    }

    @Test
    void 같은_이름의_폴더가_있으면_거절하고_아무것도_반영하지_않는다() {
        createFolderService.create(room, "민찬");

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key())))
            .isEqualTo(ErrorCode.ORGANIZE_FOLDER_NAME_CONFLICT);

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(folders()).isEqualTo(1);
        assertThat(links()).isZero();
        assertThat(repository.findActiveByRoom(room)).isPresent();
    }

    @Test
    void 선택한_사진이_사라졌으면_거절하고_일부만_반영하지_않는다() {
        jdbc.update("DELETE FROM stored_file WHERE id = ?", ids.get(1));

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key())))
            .isEqualTo(ErrorCode.ORGANIZE_TARGET_CHANGED);

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(folders()).isZero();
    }

    @Test
    void 일반_그룹이_아니거나_그룹에_없는_사진은_선택할_수_없다() {
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            List.of(new GroupRequest("unassigned", "미분류", List.of(ids.get(3)))), key())))
            .isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", List.of(ids.get(3))), key())))
            .isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);
    }

    @Test
    void 요청_형식이_틀리면_거절한다() {
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), null, key())))
            .isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), List.of(), key())))
            .isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            person(" ", ids.subList(0, 1)), key()))).isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            person("민찬", List.of(ids.get(0), ids.get(0))), key()))).isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest("person_0001", "A", List.of(ids.get(0))),
            new GroupRequest("person_0001", "B", List.of(ids.get(1)))), key()))).isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 1)), null)))
            .isEqualTo(ErrorCode.INVALID_PARAM);
    }

    @Test
    void 검토_기한이_지났으면_410이고_폴더를_만들지_않는다() {
        clock.advance(Duration.ofMinutes(31));

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key())))
            .isEqualTo(ErrorCode.ORGANIZE_REVIEW_EXPIRED);
        assertThat(folders()).isZero();
    }

    @Test
    void 분석이_끝나지_않았거나_취소된_작업은_반영할_수_없다() {
        Long other = registeredMember();
        Long otherRoom = room(other);
        List<Long> photos = jpegs(otherRoom, other, 2);
        OrganizeJob running = startPerson(otherRoom, other);

        assertThat(errorOf(() -> applyService.apply(otherRoom, other, running.publicId(),
            List.of(new GroupRequest("person_0001", "A", photos.subList(0, 1))), key())))
            .isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_READY);
    }

    @Test
    void 요청자가_아니면_404다() {
        Long guest = registeredMember();

        assertThatThrownBy(() -> applyService.apply(room, guest, job.publicId(), person("민찬", ids.subList(0, 1)), key()))
            .isInstanceOfSatisfying(SssOkException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_FOUND));
    }
}
