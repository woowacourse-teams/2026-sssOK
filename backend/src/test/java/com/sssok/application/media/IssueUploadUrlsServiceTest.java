package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.room.Room;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지는 목으로 둔다 — 서명 URL 생성은
// 어댑터의 책임이고, 여기서는 무엇을 예약하고 무엇을 걸러내는지를 본다.
@SpringBootTest
@ActiveProfiles("test")
class IssueUploadUrlsServiceTest {

    private static final String PRESIGNED = "https://storage.example.com/signed";

    @Autowired
    IssueUploadUrlsService issueUploadUrlsService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @MockitoBean
    RoomPermissionPort roomPermissionPort;

    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomId = room.getId();
        given(fileStoragePort.presignPut(any(), anyString(), any(Duration.class))).willReturn(PRESIGNED);
        given(roomPermissionPort.canUpload(any(), any())).willReturn(true);
    }

    private UploadFileCommand image(String fileName) {
        return new UploadFileCommand(fileName, "image/jpeg", 1024L);
    }

    @Test
    void 발급하면_예약_상태의_미디어가_생긴다() {
        IssueUploadUrlsResult result =
            issueUploadUrlsService.issue(roomId, hostId, List.of(image("a.jpg")), null);

        assertThat(result.issued()).hasSize(1);
        UploadUrl issued = result.issued().get(0);
        assertThat(issued.fileName()).isEqualTo("a.jpg");
        assertThat(issued.method()).isEqualTo("PUT");
        assertThat(fileRepository.findById(issued.mediaId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.RESERVED);
    }

    @Test
    void 서명한_ContentType_을_헤더로_함께_내려준다() {
        IssueUploadUrlsResult result =
            issueUploadUrlsService.issue(roomId, hostId, List.of(image("a.jpg")), null);

        // 프론트가 확장자로 추측하면 서명값과 어긋나 스토리지가 403 을 준다.
        assertThat(result.issued().get(0).headers()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void 유효기간을_초로_알려준다() {
        IssueUploadUrlsResult result =
            issueUploadUrlsService.issue(roomId, hostId, List.of(image("a.jpg")), null);

        assertThat(result.issued().get(0).expiresIn()).isEqualTo(600);
    }

    @Test
    void 걸러진_파일이_있어도_나머지는_발급한다() {
        List<UploadFileCommand> files = List.of(
            image("a.jpg"),
            new UploadFileCommand("note.pdf", "application/pdf", 1024L),
            image("b.jpg"));

        IssueUploadUrlsResult result = issueUploadUrlsService.issue(roomId, hostId, files, null);

        assertThat(result.issued()).hasSize(2);
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).fileName()).isEqualTo("note.pdf");
        assertThat(result.rejected().get(0).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void 용량을_넘긴_파일은_사유와_함께_걸러진다() {
        List<UploadFileCommand> files =
            List.of(new UploadFileCommand("big.jpg", "image/jpeg", 11L * 1024 * 1024));

        IssueUploadUrlsResult result = issueUploadUrlsService.issue(roomId, hostId, files, null);

        assertThat(result.issued()).isEmpty();
        assertThat(result.rejected().get(0).code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 크기가_0이하면_사유와_함께_걸러진다() {
        List<UploadFileCommand> files =
            List.of(new UploadFileCommand("a.jpg", "image/jpeg", 0L));

        IssueUploadUrlsResult result = issueUploadUrlsService.issue(roomId, hostId, files, null);

        assertThat(result.rejected().get(0).code()).isEqualTo("INVALID_PARAM");
    }

    @Test
    void 없는_폴더를_지정하면_요청_전체를_막는다() {
        // 일부만 담기면 어디에 들어갔는지 알 수 없어 파일별로 넘기지 않는다.
        assertThatThrownBy(() -> issueUploadUrlsService.issue(
            roomId, hostId, List.of(image("a.jpg")), List.of(-1L)))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 파일_목록이_비어있으면_예외() {
        assertThatThrownBy(() -> issueUploadUrlsService.issue(roomId, hostId, List.of(), null))
            .isInstanceOf(InvalidUploadParamException.class);
    }

    @Test
    void 업로드_권한이_없으면_예외() {
        given(roomPermissionPort.canUpload(any(), any())).willReturn(false);

        assertThatThrownBy(() ->
            issueUploadUrlsService.issue(roomId, hostId, List.of(image("a.jpg")), null))
            .isInstanceOf(UploadNotAllowedException.class);
    }
}
