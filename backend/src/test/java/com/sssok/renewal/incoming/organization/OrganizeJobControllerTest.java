package com.sssok.renewal.incoming.organization;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.common.exception.ErrorCode;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.renewal.application.organization.job.OrganizeApplyService;
import com.sssok.renewal.application.organization.job.OrganizeCancelService;
import com.sssok.renewal.application.organization.job.OrganizeJobException;
import com.sssok.renewal.application.organization.job.OrganizeJobInProgressException;
import com.sssok.renewal.application.organization.job.OrganizeJobQueryService;
import com.sssok.renewal.application.organization.job.OrganizeRateLimitedException;
import com.sssok.renewal.application.organization.job.OrganizeResultService;
import com.sssok.renewal.application.organization.job.OrganizeResultView;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import com.sssok.renewal.application.organization.job.OrganizeRetryService;
import com.sssok.renewal.application.organization.job.OrganizeStartService;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.security.TokenProvider;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 슬라이스 테스트. 서비스는 목이고, 요청 매핑·헤더·응답 형식·예외가 오류 응답으로 바뀌는지만 본다.
// 규칙 자체(멱등·사용권·기한 등)는 서비스 통합 테스트에서 검증한다.
@WebMvcTest(OrganizeJobController.class)
class OrganizeJobControllerTest {
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final UUID JOB_ID = UUID.fromString("7b44258a-81c7-4e75-9a85-16e5df65ad61");
    private static final String BEARER = "Bearer valid-token";
    private static final String KEY = "0123456789abcdef";
    private static final String BASE = "/api/v1/rooms/{roomId}/organize-jobs";
    private static final Instant T0 = Instant.parse("2026-11-04T03:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrganizeStartService startService;
    @MockitoBean
    OrganizeJobQueryService queryService;
    @MockitoBean
    OrganizeResultService resultService;
    @MockitoBean
    OrganizeCancelService cancelService;
    @MockitoBean
    OrganizeApplyService applyService;
    @MockitoBean
    OrganizeRetryService retryService;
    @MockitoBean
    OrganizeRetryPolicy retryPolicy;
    @MockitoBean
    TokenProvider tokenProvider;
    @MockitoBean
    AdminTokenProvider adminTokenProvider;
    @MockitoBean
    RoomRepository roomRepository;
    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(Room.reconstruct(ROOM_ID, null,
            RoomCode.generate(new SecureRandom()), new RoomName("우테코 회식"), RoomStatus.initial(),
            UploadPolicy.ANYONE, 1L, Instant.now(), null)));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    private static OrganizeJob job(OrganizeJobStatus status) {
        return new OrganizeJob(1L, JOB_ID, ROOM_ID, MEMBER_ID, OrganizeKind.PERSON, OrganizeTrigger.BUTTON, status,
            null, "gallery", "group_photos_by_person", "설명", 40, 5, 3, 0, null, null, null, null, null, null, null,
            null, null, null, T0, T0);
    }

    private ResultActions startByKind(String body) throws Exception {
        return mockMvc.perform(post(BASE, ROOM_ID).header("Authorization", BEARER).header("Idempotency-Key", KEY)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions startByText(String body) throws Exception {
        return mockMvc.perform(post(BASE + "/by-text", ROOM_ID).header("Authorization", BEARER)
            .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Nested
    class 시작 {

        @Test
        void 버튼으로_시작하면_202와_작업을_돌려주고_멱등_키를_서비스에_넘긴다() throws Exception {
            given(startService.startByKind(ROOM_ID, MEMBER_ID, OrganizeKind.PERSON, KEY))
                .willReturn(job(OrganizeJobStatus.QUEUED));

            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.kind").value("PERSON"))
                .andExpect(jsonPath("$.data.photoCount").value(40))
                .andExpect(jsonPath("$.data.excludedCount").value(5))
                .andExpect(jsonPath("$.data.attempt").value(1))
                .andExpect(jsonPath("$.data.jobVersion").value(3))
                .andExpect(jsonPath("$.data.retryable").value(false))
                .andExpect(jsonPath("$.data.progress").doesNotExist())
                .andExpect(jsonPath("$.data.failureCode").doesNotExist());
            verify(startService).startByKind(ROOM_ID, MEMBER_ID, OrganizeKind.PERSON, KEY);
        }

        @Test
        void 기한_필드는_값이_없어도_null로_항상_내려간다() throws Exception {
            given(startService.startByKind(anyLong(), anyLong(), any(), any())).willReturn(job(OrganizeJobStatus.QUEUED));

            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(jsonPath("$.data.retryAvailableAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reviewExpiresAt").value(nullValue()))
                .andExpect(jsonPath("$.data.resultExpiresAt").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"retryAvailableAt\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"reviewExpiresAt\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"resultExpiresAt\":null")));
        }

        @Test
        void 문장으로_시작하면_202이고_문장과_키를_서비스에_넘긴다() throws Exception {
            given(startService.startByText(ROOM_ID, MEMBER_ID, "사람별로 묶어줘", KEY))
                .willReturn(job(OrganizeJobStatus.QUEUED));

            startByText("{\"text\":\"사람별로 묶어줘\"}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
            verify(startService).startByText(ROOM_ID, MEMBER_ID, "사람별로 묶어줘", KEY);
        }

        @Test
        void 지원하지_않는_종류이거나_본문이_없으면_400이다() throws Exception {
            startByKind("{\"kind\":\"DELETE\"}").andExpect(status().isBadRequest());
            startByKind("{}").andExpect(status().isBadRequest());
            startByKind("").andExpect(status().isBadRequest());
        }

        @Test
        void 키를_보내지_않으면_서비스에_null을_넘기고_서비스가_거절한다() throws Exception {
            given(startService.startByKind(ROOM_ID, MEMBER_ID, OrganizeKind.PERSON, null))
                .willThrow(new OrganizeJobException(ErrorCode.INVALID_PARAM, "Idempotency-Key 는 16~64자여야 합니다"));

            mockMvc.perform(post(BASE, ROOM_ID).header("Authorization", BEARER)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"PERSON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        }

        @Test
        void 인증_없이_요청하면_401이다() throws Exception {
            mockMvc.perform(post(BASE, ROOM_ID).header("Idempotency-Key", KEY)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"PERSON\"}"))
                .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @CsvSource({
            "ORGANIZE_TRIAL_EXHAUSTED, 403",
            "IDEMPOTENCY_KEY_CONFLICT, 409",
            "ORGANIZE_NO_ANALYZABLE_PHOTOS, 422",
            "ORGANIZE_UNAVAILABLE, 503"
        })
        void 접수_실패는_코드에_맞는_상태로_답한다(ErrorCode code, int httpStatus) throws Exception {
            given(startService.startByKind(anyLong(), anyLong(), any(), any())).willThrow(new OrganizeJobException(code));

            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
        }

        @Test
        void 한도를_넘으면_현재_장수와_최대_장수를_메시지에_담아_422다() throws Exception {
            given(startService.startByKind(anyLong(), anyLong(), any(), any()))
                .willThrow(new OrganizeJobException(ErrorCode.ORGANIZE_TOO_MANY_PHOTOS, 201, 200));

            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZE_TOO_MANY_PHOTOS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("201")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("200")));
        }

        @Test
        void 방이_사용_중이면_409이고_본인_작업일_때만_activeJobId를_담는다() throws Exception {
            given(startService.startByKind(anyLong(), anyLong(), any(), any()))
                .willThrow(new OrganizeJobInProgressException(JOB_ID));
            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZE_JOB_IN_PROGRESS"))
                .andExpect(jsonPath("$.activeJobId").value(JOB_ID.toString()));

            org.mockito.Mockito.reset(startService);
            willThrow(new OrganizeJobInProgressException(null)).given(startService)
                .startByKind(anyLong(), anyLong(), any(), any());
            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.activeJobId").doesNotExist());
        }

        @Test
        void 요청이_너무_잦으면_429이고_Retry_After를_초로_준다() throws Exception {
            given(startService.startByKind(anyLong(), anyLong(), any(), any()))
                .willThrow(new OrganizeRateLimitedException(42));

            startByKind("{\"kind\":\"PERSON\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.code").value("ORGANIZE_RATE_LIMITED"));
        }

        @Test
        void 되묻기와_미지원은_422다() throws Exception {
            given(startService.startByText(anyLong(), anyLong(), any(), any()))
                .willThrow(new OrganizeJobException(ErrorCode.ORGANIZE_NEEDS_CLARIFICATION));
            startByText("{\"text\":\"음...\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZE_NEEDS_CLARIFICATION"));

            org.mockito.Mockito.reset(startService);
            willThrow(new OrganizeJobException(ErrorCode.ORGANIZE_UNSUPPORTED_REQUEST)).given(startService)
                .startByText(anyLong(), anyLong(), any(), any());
            startByText("{\"text\":\"지워줘\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZE_UNSUPPORTED_REQUEST"));
        }
    }

    @Nested
    class 조회 {

        @Test
        void current는_작업이_없으면_myJob이_null인_200이다() throws Exception {
            given(queryService.current(ROOM_ID, MEMBER_ID))
                .willReturn(new OrganizeJobQueryService.Current(false, 0, null, null));

            mockMvc.perform(get(BASE + "/current", ROOM_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomBusy").value(false))
                .andExpect(jsonPath("$.data.roomVersion").value(0))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"anonymousTrialAvailable\":null")))
                .andExpect(jsonPath("$.data.myJob").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"myJob\":null")));
        }

        @ParameterizedTest
        @CsvSource({"true", "false"})
        void current는_익명_체험_가능_여부를_반환한다(boolean available) throws Exception {
            given(queryService.current(ROOM_ID, MEMBER_ID))
                .willReturn(new OrganizeJobQueryService.Current(false, 0, null, available));

            mockMvc.perform(get(BASE + "/current", ROOM_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anonymousTrialAvailable").value(available));
        }

        @Test
        void current는_방_상태와_내_작업을_함께_돌려준다() throws Exception {
            given(queryService.current(ROOM_ID, MEMBER_ID))
                .willReturn(new OrganizeJobQueryService.Current(true, 8, job(OrganizeJobStatus.RUNNING), null));

            mockMvc.perform(get(BASE + "/current", ROOM_ID).header("Authorization", BEARER))
                .andExpect(jsonPath("$.data.roomBusy").value(true))
                .andExpect(jsonPath("$.data.roomVersion").value(8))
                .andExpect(jsonPath("$.data.myJob.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.myJob.status").value("RUNNING"));
        }

        @Test
        void 단건_조회는_작업을_돌려주고_없으면_404다() throws Exception {
            given(queryService.get(ROOM_ID, MEMBER_ID, JOB_ID)).willReturn(job(OrganizeJobStatus.READY));
            mockMvc.perform(get(BASE + "/{jobId}", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

            given(queryService.get(ROOM_ID, MEMBER_ID, JOB_ID))
                .willThrow(new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_FOUND));
            mockMvc.perform(get(BASE + "/{jobId}", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZE_JOB_NOT_FOUND"));
        }

        @Test
        void jobId가_UUID가_아니면_400이다() throws Exception {
            mockMvc.perform(get(BASE + "/{jobId}", ROOM_ID, "not-a-uuid").header("Authorization", BEARER))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 결과는_그룹과_미분류와_개별_오류를_담아_돌려준다() throws Exception {
            given(resultService.get(ROOM_ID, MEMBER_ID, JOB_ID)).willReturn(new OrganizeResultView(JOB_ID,
                OrganizeKind.PERSON, 1, 1, 0, List.of(), null, new OrganizeResultView.Group(null, 0, List.of()),
                List.of(new OrganizeResultView.ErrorItem(101L, "image_decode_failed"))));

            mockMvc.perform(get(BASE + "/{jobId}/result", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.resultVersion").value(1))
                .andExpect(jsonPath("$.data.groups.length()").value(0))
                .andExpect(jsonPath("$.data.unassigned.photoCount").value(0))
                .andExpect(jsonPath("$.data.errors[0].mediaId").value(101))
                .andExpect(jsonPath("$.data.errors[0].code").value("image_decode_failed"))
                .andExpect(jsonPath("$.data.smallGroups").doesNotExist());
        }

        @ParameterizedTest
        @CsvSource({
            "ORGANIZE_JOB_NOT_READY, 409",
            "ORGANIZE_REVIEW_EXPIRED, 410",
            "ORGANIZE_RESULT_EXPIRED, 410"
        })
        void 결과를_볼_수_없으면_상태에_맞게_답한다(ErrorCode code, int httpStatus) throws Exception {
            given(resultService.get(ROOM_ID, MEMBER_ID, JOB_ID)).willThrow(new OrganizeJobException(code));

            mockMvc.perform(get(BASE + "/{jobId}/result", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
        }
    }

    @Nested
    class 반영 {
        private ResultActions apply(String body) throws Exception {
            return mockMvc.perform(post(BASE + "/{jobId}/apply", ROOM_ID, JOB_ID).header("Authorization", BEARER)
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        @Test
        void 선택한_그룹을_서비스에_넘기고_생성된_폴더를_돌려준다() throws Exception {
            given(applyService.apply(eq(ROOM_ID), eq(MEMBER_ID), eq(JOB_ID), anyList(), eq(KEY))).willReturn(
                new OrganizeApplyService.Applied(job(OrganizeJobStatus.APPLIED),
                    List.of(new OrganizeApplyService.AppliedFolder(201L, "민찬", List.of(101L, 102L)))));

            apply("{\"groups\":[{\"groupId\":\"person_0001\",\"folderName\":\"민찬\",\"mediaIds\":[101,102]}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.folders[0].folderId").value(201))
                .andExpect(jsonPath("$.data.folders[0].folderName").value("민찬"))
                .andExpect(jsonPath("$.data.folders[0].mediaIds[1]").value(102));
            verify(applyService).apply(ROOM_ID, MEMBER_ID, JOB_ID,
                List.of(new OrganizeApplyService.GroupRequest("person_0001", "민찬", List.of(101L, 102L))), KEY);
        }

        @Test
        void 본문이_깨졌으면_400이다() throws Exception {
            apply("{not json").andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @CsvSource({
            "INVALID_PARAM, 400",
            "ORGANIZE_JOB_NOT_READY, 409",
            "ORGANIZE_FOLDER_NAME_CONFLICT, 409",
            "ORGANIZE_TARGET_CHANGED, 409",
            "ORGANIZE_ALREADY_APPLIED, 409",
            "IDEMPOTENCY_KEY_CONFLICT, 409",
            "ORGANIZE_REVIEW_EXPIRED, 410",
            "ORGANIZE_RESULT_EXPIRED, 410"
        })
        void 반영_실패는_코드에_맞는_상태로_답한다(ErrorCode code, int httpStatus) throws Exception {
            given(applyService.apply(anyLong(), anyLong(), any(), any(), any()))
                .willThrow(new OrganizeJobException(code, "상세"));

            apply("{\"groups\":[{\"groupId\":\"g\",\"folderName\":\"f\",\"mediaIds\":[1]}]}")
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
        }
    }

    @Nested
    class 취소와_재시도 {

        @Test
        void 취소하면_200과_닫힌_작업을_돌려준다() throws Exception {
            given(cancelService.cancel(ROOM_ID, MEMBER_ID, JOB_ID)).willReturn(job(OrganizeJobStatus.CANCELED));

            mockMvc.perform(post(BASE + "/{jobId}/cancel", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        }

        @Test
        void 반영된_작업은_취소할_수_없어_409다() throws Exception {
            given(cancelService.cancel(ROOM_ID, MEMBER_ID, JOB_ID))
                .willThrow(new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_CANCELABLE));

            mockMvc.perform(post(BASE + "/{jobId}/cancel", ROOM_ID, JOB_ID).header("Authorization", BEARER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZE_JOB_NOT_CANCELABLE"));
        }

        @Test
        void 새_실행을_예약하면_202다() throws Exception {
            given(retryService.retry(ROOM_ID, MEMBER_ID, JOB_ID, KEY))
                .willReturn(new OrganizeRetryService.Result(job(OrganizeJobStatus.QUEUED), true));

            mockMvc.perform(post(BASE + "/{jobId}/retry", ROOM_ID, JOB_ID).header("Authorization", BEARER)
                    .header("Idempotency-Key", KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
        }

        @Test
        void 이미_진행_중이면_새_실행_없이_200이다() throws Exception {
            given(retryService.retry(ROOM_ID, MEMBER_ID, JOB_ID, KEY))
                .willReturn(new OrganizeRetryService.Result(job(OrganizeJobStatus.RUNNING), false));

            mockMvc.perform(post(BASE + "/{jobId}/retry", ROOM_ID, JOB_ID).header("Authorization", BEARER)
                    .header("Idempotency-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        }

        @Test
        void 재시도_대기_중이면_429와_Retry_After다() throws Exception {
            given(retryService.retry(anyLong(), anyLong(), any(), any())).willThrow(new OrganizeRateLimitedException(5));

            mockMvc.perform(post(BASE + "/{jobId}/retry", ROOM_ID, JOB_ID).header("Authorization", BEARER)
                    .header("Idempotency-Key", KEY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"));
        }

        @ParameterizedTest
        @CsvSource({
            "ORGANIZE_RETRY_NOT_ALLOWED, 409",
            "ORGANIZE_TARGET_CHANGED, 409",
            "ORGANIZE_TRIAL_EXHAUSTED, 403",
            "ORGANIZE_JOB_NOT_FOUND, 404",
            "ORGANIZE_UNAVAILABLE, 503"
        })
        void 재시도_실패는_코드에_맞는_상태로_답한다(ErrorCode code, int httpStatus) throws Exception {
            given(retryService.retry(anyLong(), anyLong(), any(), any())).willThrow(new OrganizeJobException(code));

            mockMvc.perform(post(BASE + "/{jobId}/retry", ROOM_ID, JOB_ID).header("Authorization", BEARER)
                    .header("Idempotency-Key", KEY))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code.name()));
        }
    }

    @Test
    void availability는_더_이상_제공하지_않는다() throws Exception {
        mockMvc.perform(get(BASE + "/availability", ROOM_ID).header("Authorization", BEARER))
            .andExpect(status().isBadRequest());
    }
}
