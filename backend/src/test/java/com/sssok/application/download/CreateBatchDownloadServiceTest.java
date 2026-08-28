package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지 서명은 목으로 둔다.
@SpringBootTest
@ActiveProfiles("test")
class CreateBatchDownloadServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long UPLOADER_ID = 100L;
    private static final String PRESIGNED = "https://storage.example.com/signed";

    @Autowired
    CreateBatchDownloadService createBatchDownloadService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
    }

    private Long media(String fileName) {
        StoredFile file = StoredFile.reserve(ROOM_ID, UPLOADER_ID, fileName, "image/jpeg",
            new FileSize(1024), Instant.now());
        file.startProcessing();
        file.markReady();
        return fileRepository.save(file).getId();
    }

    @Test
    void 대상_파일마다_서명_URL을_발급한다() {
        Long media1 = media("IMG_0421.jpg");
        Long media2 = media("IMG_0420.jpg");

        List<BatchDownloadFile> files = createBatchDownloadService.create(ROOM_ID, List.of(media1, media2), null);

        assertThat(files).hasSize(2);
        assertThat(files).extracting(BatchDownloadFile::downloadUrl).containsOnly(PRESIGNED);
        assertThat(files).extracting(BatchDownloadFile::mediaId).containsExactlyInAnyOrder(media1, media2);
    }

    @Test
    void 같은_원본_파일명은_번호를_붙여_구분한다() {
        Long media1 = media("IMG_0421.jpg");
        Long media2 = media("IMG_0421.jpg");

        List<BatchDownloadFile> files = createBatchDownloadService.create(ROOM_ID, List.of(media1, media2), null);

        assertThat(files).extracting(BatchDownloadFile::fileName)
            .containsExactlyInAnyOrder("IMG_0421.jpg", "IMG_0421 (1).jpg");
    }

    @Test
    void 만료_시각은_presignedGetTtl만큼_뒤다() {
        Long media = media("IMG_0421.jpg");
        Instant before = Instant.now();

        List<BatchDownloadFile> files = createBatchDownloadService.create(ROOM_ID, List.of(media), null);

        assertThat(files.get(0).expiresAt()).isAfter(before);
    }

    @Test
    void 대상이_없으면_리졸버의_예외가_그대로_전파된다() {
        assertThatThrownBy(() -> createBatchDownloadService.create(ROOM_ID, List.of(999_999L), null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 처리중인_미디어는_대상에서_빠진다() {
        StoredFile processing = StoredFile.reserve(ROOM_ID, UPLOADER_ID, "processing.jpg", "image/jpeg",
            new FileSize(1024), Instant.now());
        processing.startProcessing();
        Long processingId = fileRepository.save(processing).getId();
        Long readyId = media("ready.jpg");

        List<BatchDownloadFile> files =
            createBatchDownloadService.create(ROOM_ID, List.of(processingId, readyId), null);

        assertThat(files).extracting(BatchDownloadFile::mediaId).containsExactly(readyId);
    }
}
