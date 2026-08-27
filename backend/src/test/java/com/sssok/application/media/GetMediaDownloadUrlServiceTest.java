package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.MediaNotReadyException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지 서명은 목으로 둔다.
@SpringBootTest
@ActiveProfiles("test")
class GetMediaDownloadUrlServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long UPLOADER_ID = 100L;
    private static final String PRESIGNED = "https://storage.example.com/signed";

    @Autowired
    GetMediaDownloadUrlService getMediaDownloadUrlService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
    }

    private StoredFile save(Long roomId, UploadStatus status) {
        StoredFile file = StoredFile.reserve(
            roomId, UPLOADER_ID, "사진.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        switch (status) {
            case PROCESSING -> file.startProcessing();
            case READY -> {
                file.startProcessing();
                file.markReady();
            }
            case FAILED -> file.failUpload();
            default -> {
            }
        }
        return fileRepository.save(file);
    }

    @Test
    void READY_상태면_발급받은_서명_URL을_그대로_반환한다() {
        StoredFile file = save(ROOM_ID, UploadStatus.READY);

        String url = getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId());

        assertThat(url).isEqualTo(PRESIGNED);
    }

    @Test
    void 원본_파일명과_MIME으로_서명을_요청한다() {
        StoredFile file = save(ROOM_ID, UploadStatus.READY);

        getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId());

        verify(fileStoragePort).presignGet(
            eq(file.getStorageKey()),
            eq("attachment; filename=\"download.jpg\"; filename*=UTF-8''%EC%82%AC%EC%A7%84.jpg"),
            eq("image/jpeg"),
            any(Duration.class));
    }

    @Test
    void PROCESSING_상태면_409() {
        StoredFile file = save(ROOM_ID, UploadStatus.PROCESSING);

        assertThatThrownBy(() -> getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(MediaNotReadyException.class);
    }

    @Test
    void RESERVED_상태면_404() {
        StoredFile file = save(ROOM_ID, UploadStatus.RESERVED);

        assertThatThrownBy(() -> getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void FAILED_상태면_404() {
        StoredFile file = save(ROOM_ID, UploadStatus.FAILED);

        assertThatThrownBy(() -> getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 존재하지_않는_미디어면_404() {
        assertThatThrownBy(() -> getMediaDownloadUrlService.getUrl(ROOM_ID, 999L))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다른_방_소속_미디어면_404() {
        StoredFile file = save(2L, UploadStatus.READY);

        assertThatThrownBy(() -> getMediaDownloadUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }
}
