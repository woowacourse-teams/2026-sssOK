package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeFixtures;
import com.sssok.renewal.application.organization.OrganizeJobStateHelper;
import com.sssok.renewal.application.organization.OrganizePostgresTest;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.application.organization.job.OrganizeApplyService.GroupRequest;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.incoming.organization.OrganizeJobDispatchListener;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

// Repository + Service 통합 테스트 (PostgreSQL). 폴더 담기가 ON CONFLICT 네이티브 쿼리라 H2 로는 성공 경로를 검증할 수 없다.
// 규칙 위반으로 거절되는 경로(이름 충돌·사진 변경·형식 오류 등)는 OrganizeApplyServiceTest(H2)에 있다.
@OrganizePostgresTest
@TestPropertySource(properties = "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==")
class OrganizeApplyServicePostgresTest extends PostgresContainerSupport {
    @Autowired
    OrganizeApplyService applyService;
    @Autowired
    CreateFolderService createFolderService;
    @Autowired
    OrganizeFixtures fixtures;
    @Autowired
    OrganizeJobStateHelper states;
    @Autowired
    OrganizeJobRepository repository;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    McpOrganizeToolCatalog catalog;
    @MockitoBean
    OrganizeJobDispatchListener dispatchListener;

    @Autowired
    com.sssok.renewal.application.organization.MutableClock clock;

    private Long host;
    private Long room;
    private List<Long> ids;
    private OrganizeJob job;

    @BeforeEach
    void setUp() {
        given(catalog.fetchTools()).willReturn(List.of(
            new OrganizeTool("gallery", "group_photos_by_person", "사람별로 사진을 묶는다"),
            new OrganizeTool("gallery", "group_similar_photos", "비슷한 사진끼리 묶는다")));
        host = fixtures.registeredMember();
        room = fixtures.room(host);
        ids = fixtures.jpegs(room, host, 4);
        job = fixtures.startPerson(room, host);
        states.ready(job, fixtures.personResult(ids.subList(0, 3), List.of(ids.get(3))));
    }

    private ErrorCode errorOf(Runnable action) {
        return org.junit.jupiter.api.Assertions.assertThrows(SssOkException.class, action::run).errorCode();
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
    void 일반_그룹을_새_폴더로_만들고_사진을_연결하며_작업을_닫는다() {
        OrganizeApplyService.Applied applied =
            applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), fixtures.key());

        assertThat(applied.job().status()).isEqualTo(OrganizeJobStatus.APPLIED);
        assertThat(applied.folders().get(0).folderName()).isEqualTo("민찬");
        assertThat(applied.folders().get(0).mediaIds()).containsExactly(ids.get(0), ids.get(1));
        assertThat(folders()).isEqualTo(1);
        assertThat(links()).isEqualTo(2);
        assertThat(repository.findActiveByRoom(room)).isEmpty();
        assertThat(repository.findRoomState(room)).get().extracting("busy", "version").containsExactly(false, 2L);
    }

    @Test
    void 같은_키로_다시_보내면_폴더를_중복_생성하지_않고_처음_결과를_돌려준다() {
        String key = fixtures.key();
        OrganizeApplyService.Applied first =
            applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key);

        OrganizeApplyService.Applied replay =
            applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key);

        assertThat(replay.folders()).isEqualTo(first.folders());
        assertThat(folders()).isEqualTo(1);
    }

    @Test
    void 같은_키에_다른_선택이면_거절한다() {
        String key = fixtures.key();
        applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), key);

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("다른", ids.subList(0, 1)), key)))
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void 다른_키로_이미_반영된_작업을_다시_반영하면_거절한다() {
        applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)), fixtures.key());

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), person("민찬", ids.subList(0, 2)),
            fixtures.key()))).isEqualTo(ErrorCode.ORGANIZE_ALREADY_APPLIED);
    }

    @Test
    void 여러_그룹을_한_번에_반영한다() {
        Long other = fixtures.registeredMember();
        Long otherRoom = fixtures.room(other);
        List<Long> photos = fixtures.jpegs(otherRoom, other, 4);
        OrganizeJob multi = fixtures.startPerson(otherRoom, other);
        states.ready(multi, objectMapper.valueToTree(Map.of(
            "groups", List.of(
                Map.of("groupId", "person_0001", "mediaIds", photos.subList(0, 2)),
                Map.of("groupId", "person_0002", "mediaIds", photos.subList(2, 4))),
            "unassignedMediaIds", List.of(), "errors", List.of(), "counts", Map.of(), "pipelineVersion", "v1")));

        OrganizeApplyService.Applied applied = applyService.apply(otherRoom, other, multi.publicId(), List.of(
            new GroupRequest("person_0001", "A", photos.subList(0, 2)),
            new GroupRequest("person_0002", "B", photos.subList(2, 4))), fixtures.key());

        assertThat(applied.folders()).extracting(OrganizeApplyService.AppliedFolder::folderName).containsExactly("A", "B");
    }

    @Test
    void 뒤쪽_그룹이_이름_충돌로_실패하면_앞_그룹의_폴더와_연결도_남기지_않는다() {
        Long other = fixtures.registeredMember();
        Long otherRoom = fixtures.room(other);
        List<Long> photos = fixtures.jpegs(otherRoom, other, 4);
        OrganizeJob multi = fixtures.startPerson(otherRoom, other);
        states.ready(multi, objectMapper.valueToTree(Map.of(
            "groups", List.of(
                Map.of("groupId", "person_0001", "mediaIds", photos.subList(0, 2)),
                Map.of("groupId", "person_0002", "mediaIds", photos.subList(2, 4))),
            "unassignedMediaIds", List.of(), "errors", List.of(), "counts", Map.of(), "pipelineVersion", "v1")));
        createFolderService.create(otherRoom, "B");

        assertThat(errorOf(() -> applyService.apply(otherRoom, other, multi.publicId(), List.of(
            new GroupRequest("person_0001", "A", photos.subList(0, 2)),
            new GroupRequest("person_0002", "B", photos.subList(2, 4))), fixtures.key())))
            .isEqualTo(ErrorCode.ORGANIZE_FOLDER_NAME_CONFLICT);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM folder WHERE room_id = ? AND name = 'A'", Long.class,
            otherRoom)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM organize_job WHERE id = ?", String.class, multi.id()))
            .isEqualTo("READY");
    }

    @Test
    void 요청_안에서_폴더_이름이_겹치면_앞_폴더도_남기지_않고_되돌린다() {
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest(null, "같은 이름", ids.subList(0, 2)),
            new GroupRequest(null, "같은 이름", ids.subList(2, 4))), fixtures.key())))
            .isEqualTo(ErrorCode.ORGANIZE_FOLDER_NAME_CONFLICT);

        assertThat(folders()).isZero();
        assertThat(links()).isZero();
        assertThat(repository.findById(job.id()).orElseThrow().status()).isEqualTo(OrganizeJobStatus.READY);
    }

    @Test
    void 소그룹과_미분류_사진도_다른_그룹과_합쳐_반영한다() {
        jdbc.update("UPDATE organize_job SET result = ? WHERE id = ?", objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "similar_0001", "mediaIds", List.of(ids.get(0), ids.get(1)))),
            "smallGroupMediaIds", List.of(List.of(ids.get(2))),
            "unassignedMediaIds", List.of(ids.get(3)), "errors", List.of())).toString(), job.id());

        var applied = applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest(null, "소그룹+미분류", List.of(ids.get(2), ids.get(3))),
            new GroupRequest(null, "그룹+소그룹", List.of(ids.get(0), ids.get(2)))), fixtures.key());

        assertThat(applied.folders()).hasSize(2);
        assertThat(links()).isEqualTo(4);
        assertThat(repository.findById(job.id()).orElseThrow().status()).isEqualTo(OrganizeJobStatus.APPLIED);
    }

    @Test
    void 같은_키와_선택이라도_다른_작업에_사용하면_충돌이다() {
        String key = fixtures.key();
        var selected = person("민찬", ids.subList(0, 2));
        applyService.apply(room, host, job.publicId(), selected, key);
        clock.advance(java.time.Duration.ofSeconds(61));
        OrganizeJob next = fixtures.startPerson(room, host);
        states.ready(next, fixtures.personResult(ids.subList(0, 3), List.of(ids.get(3))));

        assertThat(errorOf(() -> applyService.apply(room, host, next.publicId(), selected, key)))
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(repository.findById(next.id()).orElseThrow().status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(folders()).isEqualTo(1);
    }

    @Test
    void 그룹_ID와_폴더명의_구분자_문자가_달라도_같은_입력으로_취급하지_않는다() {
        // 검토 결과의 그룹 ID는 외부 분석 서버가 정하는 문자열이다.
        jdbc.update("UPDATE organize_job SET result = ? WHERE id = ?", objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "a|b", "mediaIds", ids), Map.of("groupId", "a", "mediaIds", ids)),
            "unassignedMediaIds", List.of(), "errors", List.of())).toString(), job.id());
        String key = fixtures.key();
        applyService.apply(room, host, job.publicId(), List.of(new GroupRequest("a|b", "c", ids)), key);

        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            List.of(new GroupRequest("a", "b|c", ids)), key))).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(folders()).isEqualTo(1);
    }

    @Test
    void 결과_전체를_합치고_분리하며_순서와_groupId가_달라도_재전송한다() {
        jdbc.update("UPDATE organize_job SET result = ? WHERE id = ?", objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "a", "mediaIds", List.of(ids.get(0))),
                Map.of("groupId", "b", "mediaIds", List.of(ids.get(1)))),
            "smallGroupMediaIds", List.of(List.of(ids.get(2))),
            "unassignedMediaIds", List.of(ids.get(3)), "errors", List.of())).toString(), job.id());
        String key = fixtures.key();
        var first = applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest(null, "합치기", ids),
            new GroupRequest(null, "일부 선택", List.of(ids.get(0), ids.get(2)))), key);
        assertThat(first.folders()).extracting(OrganizeApplyService.AppliedFolder::mediaIds)
            .containsExactly(ids, List.of(ids.get(0), ids.get(2)));
        var replay = applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest("edited", "일부 선택", List.of(ids.get(2), ids.get(0))),
            new GroupRequest("edited", "합치기", ids.reversed())), key);
        assertThat(replay.folders()).isEqualTo(first.folders());
        assertThat(links()).isEqualTo(6);
        assertThat(folders()).isEqualTo(2);
    }

    @Test
    void 같은_원본_그룹을_여러_폴더로_나눌_수_있다() {
        var applied = applyService.apply(room, host, job.publicId(), List.of(
            new GroupRequest("person_0001", "A", ids.subList(0, 2)),
            new GroupRequest("person_0001", "B", List.of(ids.get(2)))), fixtures.key());
        assertThat(applied.folders()).hasSize(2);
        assertThat(links()).isEqualTo(3);
    }

    @Test
    void 배포_전_해시의_기존_요청도_재전송할_수_있다() throws Exception {
        String key = fixtures.key();
        var selected = person("민찬", ids.subList(0, 2));
        String legacyHash = OrganizeIdempotency.hash("APPLY:" + job.publicId() + ":"
            + objectMapper.writeValueAsString(selected));
        var response = List.of(new OrganizeApplyService.AppliedFolder(999L, "민찬", ids.subList(0, 2)));
        repository.saveIdempotency(host, room, OrganizeIdempotency.APPLY, key, legacyHash, job.id(),
            objectMapper.writeValueAsString(response), clock.instant());
        assertThat(applyService.apply(room, host, job.publicId(),
            person("민찬", List.of(ids.get(1), ids.get(0))), key).folders()).isEqualTo(response);
        assertThat(folders()).isZero();
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            person("변경", ids.subList(0, 2)), key))).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void 오류_항목에만_있는_사진은_반영할_수_없다() {
        jdbc.update("UPDATE organize_job SET result = ? WHERE id = ?", objectMapper.valueToTree(Map.of(
            "groups", List.of(), "unassignedMediaIds", List.of(),
            "errors", List.of(Map.of("mediaId", ids.get(0), "code", "FAILED")))).toString(), job.id());
        assertThat(errorOf(() -> applyService.apply(room, host, job.publicId(),
            List.of(new GroupRequest(null, "오류", List.of(ids.get(0)))), fixtures.key())))
            .isEqualTo(ErrorCode.INVALID_PARAM);
        assertThat(folders()).isZero();
        assertThat(repository.findById(job.id()).orElseThrow().status()).isEqualTo(OrganizeJobStatus.READY);
    }

}
