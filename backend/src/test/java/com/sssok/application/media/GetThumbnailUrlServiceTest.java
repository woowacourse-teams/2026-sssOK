package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.ThumbnailNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class GetThumbnailUrlServiceTest {

    private static final Long ROOM_ID = 730L;
    private static final Long OTHER_ROOM_ID = 731L;
    private static final String PRESIGNED = "https://storage.example.com/thumb";

    @Autowired
    GetThumbnailUrlService getThumbnailUrlService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
    }

    @Test
    void 썸네일이_있으면_서명_URL을_반환한다() {
        StoredFile file = withThumbnail(ROOM_ID);

        String url = getThumbnailUrlService.getUrl(ROOM_ID, file.getId());

        assertThat(url).isEqualTo(PRESIGNED);
    }

    // 목록 타일에 그리는 용도라 저장 대화상자가 뜨면 안 된다. 원본 다운로드는 attachment 다.
    @Test
    void inline로_서명한다() {
        StoredFile file = withThumbnail(ROOM_ID);

        getThumbnailUrlService.getUrl(ROOM_ID, file.getId());

        verify(fileStoragePort).presignGet(
            eq(file.getStorageKey().thumbnail()), eq("inline"), eq("image/jpeg"),
            any(Duration.class));
    }

    // 미디어는 있는데 썸네일만 없는 경우다. 없는 미디어와 구분해서 알려준다.
    @Test
    void 아직_썸네일이_없으면_THUMBNAIL_NOT_FOUND() {
        StoredFile file = processing(ROOM_ID);

        assertThatThrownBy(() -> getThumbnailUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(ThumbnailNotFoundException.class);
    }

    @Test
    void 없는_미디어면_MEDIA_NOT_FOUND() {
        assertThatThrownBy(() -> getThumbnailUrlService.getUrl(ROOM_ID, 999_999L))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다른_방의_미디어면_MEDIA_NOT_FOUND() {
        StoredFile file = withThumbnail(OTHER_ROOM_ID);

        assertThatThrownBy(() -> getThumbnailUrlService.getUrl(ROOM_ID, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    private StoredFile processing(Long roomId) {
        StoredFile file = StoredFile.reserve(
            roomId, 1L, "사진.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        file.startProcessing();
        return fileRepository.save(file);
    }

    private StoredFile withThumbnail(Long roomId) {
        StoredFile file = processing(roomId);
        file.completeProcessing(new ProcessedMedia(file.getStorageKey().thumbnail(), 1200, 900, null, null));
        return fileRepository.save(file);
    }
}
