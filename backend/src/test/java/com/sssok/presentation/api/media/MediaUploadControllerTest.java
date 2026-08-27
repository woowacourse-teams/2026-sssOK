package com.sssok.presentation.api.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.media.CompleteUploadResult;
import com.sssok.application.media.CompleteUploadService;
import com.sssok.application.media.FailedMedia;
import com.sssok.application.media.IssueUploadUrlsResult;
import com.sssok.application.media.IssueUploadUrlsService;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.RejectedFile;
import com.sssok.application.media.UploadUrl;
import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.file.UploadRejectionReason;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(MediaUploadController.class)
class MediaUploadControllerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 5012L;
    private static final String BEARER = "Bearer valid-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IssueUploadUrlsService issueUploadUrlsService;

    @MockitoBean
    CompleteUploadService completeUploadService;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @BeforeEach
    void setUp() {
        given(tokenProvider.parse("valid-token")).willReturn(MEMBER_ID);
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(RoomMember.reconstruct(1L, ROOM_ID, MEMBER_ID, Instant.now())));
    }

    private Room activeRoom() {
        return Room.reconstruct(ROOM_ID, null, RoomCode.generate(new SecureRandom()),
            new RoomName("우테코 회식"), RoomStatus.initial(),
            new RoomExpiration(Instant.now().plusSeconds(3600)), UploadPolicy.ANYONE,
            1L, Instant.now(), null);
    }

    private ResultActions issueUploadUrls(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions completeUpload(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/media", ROOM_ID)
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void 발급하면_200과_issued_rejected_를_반환한다() throws Exception {
        IssueUploadUrlsResult result = new IssueUploadUrlsResult(
            List.of(new UploadUrl(MEDIA_ID, "a.jpg", "https://storage/signed", "PUT",
                Map.of("Content-Type", "image/jpeg"), 600)),
            List.of(RejectedFile.of("note.pdf", UploadRejectionReason.UNSUPPORTED_MEDIA_TYPE)));
        given(issueUploadUrlsService.issue(anyLong(), anyLong(), anyList(), any())).willReturn(result);

        issueUploadUrls("{\"files\":[{\"fileName\":\"a.jpg\",\"mimeType\":\"image/jpeg\",\"size\":1024}]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.issued[0].mediaId").value(MEDIA_ID))
            .andExpect(jsonPath("$.data.issued[0].method").value("PUT"))
            .andExpect(jsonPath("$.data.issued[0].headers['Content-Type']").value("image/jpeg"))
            .andExpect(jsonPath("$.data.issued[0].expiresIn").value(600))
            .andExpect(jsonPath("$.data.rejected[0].code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 파일이_비어있으면_400과_INVALID_PARAM() throws Exception {
        given(issueUploadUrlsService.issue(anyLong(), anyLong(), any(), any()))
            .willThrow(new InvalidUploadParamException("업로드할 파일이 없습니다"));

        issueUploadUrls("{\"files\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    @Test
    void 업로드_권한이_없으면_403과_UPLOAD_NOT_ALLOWED() throws Exception {
        given(issueUploadUrlsService.issue(anyLong(), anyLong(), anyList(), any()))
            .willThrow(new UploadNotAllowedException());

        issueUploadUrls("{\"files\":[{\"fileName\":\"a.jpg\",\"mimeType\":\"image/jpeg\",\"size\":1}]}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UPLOAD_NOT_ALLOWED"));
    }

    @Test
    void 완료_등록하면_201과_registered_failed_를_반환한다() throws Exception {
        MediaDetail detail = new MediaDetail(MEDIA_ID, "IMAGE", "a.jpg", "image/jpeg", 1024L,
            null, null, null, null, null, List.of(31L), MEMBER_ID, "로지", "PROCESSING", Instant.now());
        CompleteUploadResult result = new CompleteUploadResult(List.of(detail),
            List.of(FailedMedia.of(5013L, UploadRejectionReason.UPLOAD_NOT_COMPLETED)));
        given(completeUploadService.complete(anyLong(), anyLong(), anyList())).willReturn(result);

        completeUpload("{\"mediaIds\":[5012,5013]}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.registered[0].mediaId").value(MEDIA_ID))
            .andExpect(jsonPath("$.data.registered[0].status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.registered[0].folderIds[0]").value(31))
            .andExpect(jsonPath("$.data.failed[0].code").value("UPLOAD_NOT_COMPLETED"));
    }

    @Test
    void 남의_예약을_등록하면_403과_MEDIA_FORBIDDEN() throws Exception {
        given(completeUploadService.complete(anyLong(), anyLong(), anyList()))
            .willThrow(new MediaForbiddenException());

        completeUpload("{\"mediaIds\":[5012]}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MEDIA_FORBIDDEN"));
    }





    @Test
    void 인증_없이_요청하면_401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/media/upload-urls", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":[]}"))
            .andExpect(status().isUnauthorized());
    }
}
