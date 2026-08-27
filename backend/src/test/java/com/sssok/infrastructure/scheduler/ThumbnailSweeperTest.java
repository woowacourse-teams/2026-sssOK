package com.sssok.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 서버가 재시작되거나 워커 스레드가 죽으면 그 사진은 영영 PROCESSING 에 남는다.
// 이 배치가 유일한 회수 경로라, 실제로 회수되는지 확인해둔다.
@SpringBootTest
@ActiveProfiles("test")
class ThumbnailSweeperTest {

    private static final Long ROOM_ID = 740L;

    @Autowired
    ThumbnailSweeper thumbnailSweeper;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    // 원본을 읽을 때마다 새 스트림을 줘야 한다. 같은 스트림을 재사용하면 두 번째 읽기가 빈 값이 된다.
    private void givenOriginal() {
        byte[] content = image();
        given(fileStoragePort.openDownloadStream(any()))
            .willAnswer(call -> new ByteArrayInputStream(content));
        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willReturn(new AbortableOutputStream() {
                @Override
                public void write(int b) {
                }

                @Override
                public void abort() {
                }
            });
    }

    @Test
    void 오래_PROCESSING에_남은_미디어를_다시_처리한다() {
        StoredFile stuck = processing(Instant.now().minus(30, ChronoUnit.MINUTES));
        givenOriginal();

        thumbnailSweeper.sweep();

        assertThat(reload(stuck).getStatus()).isEqualTo(UploadStatus.READY);
    }

    // 방금 등록된 것은 비동기 워커가 아직 처리 중일 수 있다. 배치가 끼어들면 같은 일을 두 번 한다.
    @Test
    void 방금_등록된_미디어는_건드리지_않는다() {
        StoredFile fresh = processing(Instant.now());
        givenOriginal();

        thumbnailSweeper.sweep();

        assertThat(reload(fresh).getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    private StoredFile processing(Instant createdAt) {
        StoredFile file = StoredFile.reserve(
            ROOM_ID, 1L, "사진.jpg", "image/jpeg", new FileSize(1024), createdAt);
        file.startProcessing();
        StoredFile saved = fileRepository.save(file);
        // BaseEntity 가 @PrePersist 로 지금 시각을 넣기 때문에, 오래된 행은 직접 만들어야 한다.
        jdbcTemplate.update("UPDATE stored_file SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.from(createdAt), saved.getId());
        return saved;
    }

    private StoredFile reload(StoredFile file) {
        return fileRepository.findById(file.getId()).orElseThrow();
    }

    private byte[] image() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
