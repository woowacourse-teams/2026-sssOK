package com.sssok.renewal.application.media.upload.issue;

import com.sssok.renewal.application.media.upload.shared.UploadUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.domain.media.exception.UploadFolderNotFoundException;
import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import com.sssok.renewal.application.media.upload.exception.UploadNotAllowedException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.room.Room;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.support.H2IntegrationTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지는 목으로 둔다 — 서명 URL 생성은
// 스토리지 클라이언트의 책임이고, 여기서는 무엇을 예약하고 무엇을 걸러내는지를 본다.
@H2IntegrationTest
// 다른 테스트 컨텍스트가 종료되며 공용 H2 스키마를 지우는 영향을 받지 않는다.
@org.springframework.test.context.TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:upload-url-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
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
    FileStorage fileStorage;

    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();
        roomId = room.getId();
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class))).willReturn(PRESIGNED);
    }

    private UploadCandidate image(String fileName) {
        return new UploadCandidate(fileName, "image/jpeg", 1024L);
    }

    @Test
    void 발급하면_예약_상태의_미디어가_생긴다() {
        IssueUploadUrlsResult result =
            issue(roomId, hostId, List.of(image("a.jpg")), null);

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
            issue(roomId, hostId, List.of(image("a.jpg")), null);

        // 프론트가 확장자로 추측하면 서명값과 어긋나 스토리지가 403 을 준다.
        assertThat(result.issued().get(0).headers()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void 유효기간을_초로_알려준다() {
        IssueUploadUrlsResult result =
            issue(roomId, hostId, List.of(image("a.jpg")), null);

        assertThat(result.issued().get(0).expiresIn()).isEqualTo(600);
    }

    @Test
    void 걸러진_파일이_있어도_나머지는_발급한다() {
        List<UploadCandidate> files = List.of(
            image("a.jpg"),
            new UploadCandidate("note.pdf", "application/pdf", 1024L),
            image("b.jpg"));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.issued()).hasSize(2);
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).fileName()).isEqualTo("note.pdf");
        assertThat(result.rejected().get(0).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void 용량을_넘긴_파일은_사유와_함께_걸러진다() {
        List<UploadCandidate> files =
            List.of(new UploadCandidate("big.jpg", "image/jpeg", 20L * 1024 * 1024 + 1));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.issued()).isEmpty();
        assertThat(result.rejected().get(0).code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 상한과_같은_20MB_이미지는_발급된다() {
        List<UploadCandidate> files =
            List.of(new UploadCandidate("big.jpg", "image/jpeg", 20L * 1024 * 1024));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.rejected()).isEmpty();
        assertThat(result.issued()).hasSize(1);
    }

    @Test
    void 크기가_0이하면_사유와_함께_걸러진다() {
        List<UploadCandidate> files =
            List.of(new UploadCandidate("a.jpg", "image/jpeg", 0L));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.rejected().get(0).code()).isEqualTo("INVALID_PARAM");
    }

    @Test
    void 크기가_없으면_사유와_함께_걸러진다() {
        List<UploadCandidate> files =
            List.of(new UploadCandidate("a.jpg", "image/jpeg", null), image("b.jpg"));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.issued()).hasSize(1);
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).fileName()).isEqualTo("a.jpg");
        assertThat(result.rejected().get(0).code()).isEqualTo("INVALID_PARAM");
    }

    @Test
    void 파일명이_비면_사유와_함께_걸러진다() {
        List<UploadCandidate> files = List.of(
            new UploadCandidate(null, "image/jpeg", 1024L),
            new UploadCandidate("  ", "image/jpeg", 1024L),
            image("b.jpg"));

        IssueUploadUrlsResult result = issue(roomId, hostId, files, null);

        assertThat(result.issued()).hasSize(1);
        assertThat(result.rejected()).hasSize(2);
        assertThat(result.rejected()).allSatisfy(
            file -> assertThat(file.code()).isEqualTo("INVALID_PARAM"));
    }

    @Test
    void 없는_폴더를_지정하면_요청_전체를_막는다() {
        // 일부만 담기면 어디에 들어갔는지 알 수 없어 파일별로 넘기지 않는다.
        assertThatThrownBy(() -> issue(
            roomId, hostId, List.of(image("a.jpg")), List.of(-1L)))
            .isInstanceOf(UploadFolderNotFoundException.class);
    }

    @Test
    void 파일_목록이_비어있으면_예외() {
        assertThatThrownBy(() -> issue(roomId, hostId, List.of(), null))
            .isInstanceOf(InvalidUploadRequestException.class);
    }

    @Test
    void 업로드_권한이_없으면_예외() {
        roomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        hostId = -1L;

        assertThatThrownBy(() ->
            issue(roomId, hostId, List.of(image("a.jpg")), null))
            .isInstanceOf(UploadNotAllowedException.class);
    }
    @Test
    void 빈_파일_목록은_권한과_폴더보다_먼저_검증한다() {
        assertThatThrownBy(() -> issue(-1L, -1L, List.of(), List.of(-1L)))
            .isInstanceOf(InvalidUploadRequestException.class).hasMessage("업로드할 파일이 없습니다");
        assertThatThrownBy(() -> issue(-1L, -1L, null, List.of(-1L)))
            .isInstanceOf(InvalidUploadRequestException.class).hasMessage("업로드할 파일이 없습니다");
        assertThatThrownBy(() -> issue(-1L, -1L, java.util.Arrays.asList((UploadCandidate) null), List.of(-1L)))
            .isInstanceOf(InvalidUploadRequestException.class).hasMessage("파일 정보가 올바르지 않습니다");
    }

    @Test
    void 권한은_폴더와_파일별_검증보다_먼저_검증한다() {
        Long hostOnlyRoomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        assertThatThrownBy(() -> issue(hostOnlyRoomId, -1L,
            List.of(new UploadCandidate(" ", "application/pdf", 0L)), List.of(-1L)))
            .isInstanceOf(UploadNotAllowedException.class);
    }

    private IssueUploadUrlsResult issue(Long roomId, Long uploaderId, List<UploadCandidate> files, List<Long> folderIds) {
        return issueUploadUrlsService.issue(new IssueUploadUrlsCommand(roomId, uploaderId, files, folderIds));
    }
}
