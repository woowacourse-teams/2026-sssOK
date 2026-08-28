package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지만 목으로 두고 실제로 이미지를 줄여본다.
// 자동 기동(ThumbnailTrigger)은 test 프로파일에서 꺼져 있어, 여기서 직접 불러 결과를 확인한다.
@SpringBootTest
@ActiveProfiles("test")
class GenerateThumbnailServiceTest {

    private static final Long ROOM_ID = 720L;
    private static final Long UPLOADER_ID = 720L;

    @Autowired
    GenerateThumbnailService generateThumbnailService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    private ByteArrayOutputStream uploaded;

    @BeforeEach
    void setUp() {
        uploaded = new ByteArrayOutputStream();
        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willReturn(new AbortableOutputStream() {
                @Override
                public void write(int b) {
                    uploaded.write(b);
                }

                @Override
                public void abort() {
                }
            });
    }

    @Test
    void 썸네일을_만들어_올리고_READY로_넘긴다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(file.getStorageKey(), image(1200, 900, "jpg"));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.READY);
        assertThat(after.getThumbnailKey()).isNotNull();
        verify(fileStoragePort).openUploadStream(eq(after.getThumbnailKey()), eq("image/jpeg"));
    }

    // 클라이언트가 자리를 미리 잡는 데 쓰는 값이라, 썸네일이 아니라 원본 크기여야 한다.
    @Test
    void 썸네일이_아니라_원본의_크기를_저장한다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(1200, 900, "jpg"));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getWidth()).isEqualTo(1200);
        assertThat(after.getHeight()).isEqualTo(900);
    }

    @Test
    void 썸네일은_원본보다_작다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        byte[] original = image(1200, 900, "jpg");
        givenOriginal(original);

        generateThumbnailService.generate(file.getId());

        assertThat(widthOf(uploadedThumbnail())).isEqualTo(400);
    }

    // 확대한 썸네일은 원본보다 크면서 더 흐리기만 하다.
    @Test
    void 원본이_이미_작으면_늘리지_않는다() {
        StoredFile file = processing("작은사진.jpg", "image/jpeg");
        givenOriginal(image(120, 90, "jpg"));

        generateThumbnailService.generate(file.getId());

        assertThat(widthOf(uploadedThumbnail())).isEqualTo(120);
    }

    // PNG 를 JPEG 로 바꾸면 투명한 부분이 검게 나온다.
    // 선언한 Content-Type 만 보면 안 된다 — PNG 라고 말하고 JPEG 바이트를 올려도 통과해버린다.
    @Test
    void 원본과_같은_형식으로_저장한다() {
        StoredFile file = processing("사진.png", "image/png");
        givenOriginal(image(800, 600, "png"));

        generateThumbnailService.generate(file.getId());

        verify(fileStoragePort).openUploadStream(any(), eq("image/png"));
        assertThat(formatOf(uploadedThumbnail())).isEqualToIgnoringCase("png");
    }

    @Test
    void JPEG_원본은_JPEG_썸네일이_된다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(800, 600, "jpg"));

        generateThumbnailService.generate(file.getId());

        assertThat(formatOf(uploadedThumbnail())).isEqualToIgnoringCase("jpeg");
    }

    // 프레임을 뽑으려면 별도 도구가 필요하다. PROCESSING 에 두면 다운로드가 영영 409 로 막힌다.
    @Test
    void 영상은_썸네일_없이_READY로_넘긴다() {
        StoredFile file = processing("영상.mp4", "video/mp4");

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.READY);
        assertThat(after.getThumbnailKey()).isNull();
        verify(fileStoragePort, never()).openDownloadStream(any());
    }

    // 다시 시도해도 결과가 같은 유일한 경우다.
    @Test
    void 이미지가_깨졌으면_FAILED로_확정한다() {
        StoredFile file = processing("깨진사진.jpg", "image/jpeg");
        givenOriginal("이건 이미지가 아니다".getBytes());

        generateThumbnailService.generate(file.getId());

        assertThat(reload(file).getStatus()).isEqualTo(UploadStatus.FAILED);
    }

    // 여기서 FAILED 로 내리면 멀쩡히 올라간 사진이 목록에서 사라진다. 회수 배치가 다시 태운다.
    @Test
    void 원본을_읽지_못하면_PROCESSING으로_남긴다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        given(fileStoragePort.openDownloadStream(any())).willThrow(new RuntimeException("없는 키"));

        generateThumbnailService.generate(file.getId());

        assertThat(reload(file).getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 스토리지가_예외를_던져도_PROCESSING으로_남긴다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        given(fileStoragePort.openDownloadStream(any())).willThrow(new RuntimeException("R2 장애"));

        generateThumbnailService.generate(file.getId());

        assertThat(reload(file).getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    // 회수 배치가 같은 미디어를 두 번 집어도 안전해야 한다.
    @Test
    void 이미_READY인_미디어는_건드리지_않는다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        file.completeProcessing(new ProcessedMedia(file.getStorageKey().thumbnail(), 100, 100, null, null));
        fileRepository.save(file);

        generateThumbnailService.generate(file.getId());

        verify(fileStoragePort, never()).openDownloadStream(any());
        verify(fileStoragePort, never()).openUploadStream(any(), anyString());
    }

    @Test
    void 없는_미디어면_아무것도_하지_않는다() {
        generateThumbnailService.generate(999_999L);

        verify(fileStoragePort, never()).openDownloadStream(any());
    }

    private StoredFile processing(String fileName, String mimeType) {
        StoredFile file = StoredFile.reserve(
            ROOM_ID, UPLOADER_ID, fileName, mimeType, new FileSize(1024), Instant.now());
        file.startProcessing();
        return fileRepository.save(file);
    }

    private StoredFile reload(StoredFile file) {
        return fileRepository.findById(file.getId()).orElseThrow();
    }

    // 업로드 스트림에 실제로 써 넣은 바이트를 꺼낸다.
    private byte[] uploadedThumbnail() {
        return uploaded.toByteArray();
    }

    // 원본을 읽을 때마다 새 스트림을 줘야 한다. 같은 스트림을 재사용하면 두 번째 읽기가 빈 값이 된다.
    private void givenOriginal(StorageKey key, byte[] content) {
        given(fileStoragePort.openDownloadStream(key))
            .willAnswer(call -> new ByteArrayInputStream(content));
    }

    private void givenOriginal(byte[] content) {
        given(fileStoragePort.openDownloadStream(any()))
            .willAnswer(call -> new ByteArrayInputStream(content));
    }

    private int widthOf(byte[] image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image)).getWidth();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 바이트를 직접 열어 실제로 무슨 형식으로 인코딩됐는지 본다.
    private String formatOf(byte[] image) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(
            new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            assertThat(readers).hasNext();
            return readers.next().getFormatName();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] image(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
