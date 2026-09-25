package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.VideoFrameExtractorPort;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractedFrame;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractionResult;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.StorageKey;
import java.util.LinkedHashMap;
import java.util.Map;
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
import java.math.BigDecimal;
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

    // ffmpeg 실행 파일은 테스트가 도는 환경에 없을 수 있다. 추출기 자체는
    // FfmpegVideoFrameExtractorTest 가 실물로 검증하고, 여기서는 워커의 분기만 본다.
    @MockitoBean
    VideoFrameExtractorPort videoFrameExtractor;

    // 사진 한 장에서 썸네일과 프리뷰 두 벌이 올라간다. 하나의 스트림에 모으면 둘이 이어 붙어
    // 어느 쪽도 이미지로 읽히지 않으므로, 올라간 키별로 따로 담는다.
    private Map<StorageKey, ByteArrayOutputStream> uploaded;

    @BeforeEach
    void setUp() {
        uploaded = new LinkedHashMap<>();
        // 영상 경로는 원본을 내려받는 대신 이 주소를 추출기에 넘긴다.
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any()))
            .willReturn("https://r2.example.com/원본?sig=아무거나");
        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willAnswer(call -> {
                ByteArrayOutputStream target = uploaded.computeIfAbsent(
                    call.getArgument(0), key -> new ByteArrayOutputStream());
                return new AbortableOutputStream() {
                    @Override
                    public void write(int b) {
                        target.write(b);
                    }

                    @Override
                    public void abort() {
                    }
                };
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
        verify(fileStoragePort).openUploadStream(eq(after.getThumbnailKey()), eq("image/webp"));
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

    // 예전에는 원본과 같은 형식으로 줄였다. PNG 를 JPEG 로 바꾸면 투명한 부분이 검게 나와서였는데,
    // 그 탓에 파생본 형식이 업로더의 카메라 설정에 끌려다녔다. WebP 는 손실 압축에서도 알파를
    // 담을 수 있어 하나로 고정할 수 있다 (#291).
    //
    // 선언한 Content-Type 만 보면 안 된다 — WebP 라고 말하고 PNG 바이트를 올려도 통과해버린다.
    @Test
    void PNG_원본도_WebP_파생본이_된다() {
        StoredFile file = processing("사진.png", "image/png");
        givenOriginal(image(800, 600, "png"));

        generateThumbnailService.generate(file.getId());

        verify(fileStoragePort, times(2)).openUploadStream(any(), eq("image/webp"));
        assertThat(formatOf(uploadedThumbnail())).isEqualToIgnoringCase("webp");
        assertThat(formatOf(uploadedPreview())).isEqualToIgnoringCase("webp");
    }

    @Test
    void JPEG_원본도_WebP_파생본이_된다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(800, 600, "jpg"));

        generateThumbnailService.generate(file.getId());

        assertThat(formatOf(uploadedThumbnail())).isEqualToIgnoringCase("webp");
    }

    // 목록 타일과 상세 모달은 필요한 해상도가 다르다. 한 벌로 합치면 타일이 무겁거나 모달이 흐리다.
    @Test
    void 썸네일과_프리뷰를_각각_올리고_두_키를_모두_저장한다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(3000, 2000, "jpg"));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getThumbnailKey()).isEqualTo(file.getStorageKey().thumbnail("webp"));
        assertThat(after.getPreviewKey()).isEqualTo(file.getStorageKey().preview("webp"));
        assertThat(widthOf(uploadedThumbnail())).isEqualTo(400);
        assertThat(widthOf(uploadedPreview())).isEqualTo(1600);
    }

    // 파생본이 첫 프레임만 남은 정지 이미지라, 상세 화면에 그걸 띄우면 움직이던 것이 멈춘다.
    // 목록 타일은 원래 정지였으니 썸네일은 그대로 만든다.
    @Test
    void GIF는_프리뷰를_만들지_않는다() {
        StoredFile file = processing("움짤.gif", "image/gif");
        givenOriginal(image(800, 600, "gif"));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getThumbnailKey()).isNotNull();
        assertThat(after.getPreviewKey()).isNull();
        verify(fileStoragePort, times(1)).openUploadStream(any(), anyString());
    }

    @Test
    void 영상은_뽑아낸_프레임을_썸네일로_올리고_READY로_넘긴다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        givenExtractedFrame(new ExtractedFrame(1920, 1080, 12, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.READY);
        // 추출기가 JPEG 프레임을 주므로 영상 썸네일만 파생본 포맷 설정을 따르지 않는다.
        assertThat(after.getThumbnailKey()).isEqualTo(file.getStorageKey().thumbnail("jpg"));
        assertThat(after.getPreviewKey()).isNull();
    }

    // 클라이언트가 자리를 미리 잡는 데 쓰는 값이라, 썸네일이 아니라 원본 영상의 크기여야 한다.
    @Test
    void 영상은_썸네일이_아니라_원본의_크기를_저장한다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        givenExtractedFrame(new ExtractedFrame(1920, 1080, 12, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getWidth()).isEqualTo(1920);
        assertThat(after.getHeight()).isEqualTo(1080);
    }

    @Test
    void 영상은_재생시간을_저장한다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        givenExtractedFrame(new ExtractedFrame(1920, 1080, 12, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        assertThat(reload(file).getDurationSeconds()).isEqualTo(12);
    }

    // 세로로 찍은 영상이 가로로 눕지 않아야 한다. 추출기가 회전을 반영해 알려준 대로 저장한다.
    @Test
    void 세로로_찍은_영상은_세로_크기로_저장한다() {
        StoredFile file = processing("세로영상.mov", "video/quicktime");
        givenExtractedFrame(new ExtractedFrame(1080, 1920, 5, null, null, image(400, 711, "jpg")));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getWidth()).isEqualTo(1080);
        assertThat(after.getHeight()).isEqualTo(1920);
    }

    // 원본이 video/mp4 여도 썸네일 자체는 JPEG 다. 원본 타입으로 올리면 브라우저가 그리지 못한다.
    @Test
    void 영상_썸네일은_JPEG로_올린다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        givenExtractedFrame(new ExtractedFrame(1920, 1080, 12, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        verify(fileStoragePort).openUploadStream(any(), eq("image/jpeg"));
    }

    // 영상은 최대 1GB 라, 통째로 내려받으면 힙이 터진다. 서명 URL 만 넘겨야 한다.
    @Test
    void 영상_원본은_통째로_내려받지_않는다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        givenExtractedFrame(new ExtractedFrame(1920, 1080, 12, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        verify(fileStoragePort, never()).openDownloadStream(any());
    }

    // FAILED 로 내리면 원본이 멀쩡한데 목록에서 사라지고, PROCESSING 에 두면 회수 배치가
    // 같은 결과를 내는 작업을 5분마다 영원히 다시 집어 든다.
    @Test
    void 프레임을_뽑지_못한_영상은_썸네일_없이_READY로_넘긴다() {
        StoredFile file = processing("깨진영상.mp4", "video/mp4");
        given(videoFrameExtractor.extractFirstFrame(anyString(), anyInt()))
            .willReturn(ExtractionResult.unreadable());

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.READY);
        assertThat(after.getThumbnailKey()).isNull();
        assertThat(after.getDurationSeconds()).isNull();
    }

    // 파생본 생성이 실패해도 업로드 완료 자체는 되돌리지 않는다 (#291 완료 조건).
    // 등록은 이미 커밋돼 있고 워커는 그 밖에서 도므로, 여기서 예외가 밖으로 나가면 안 된다.
    // PROCESSING 으로 남겨 회수 배치가 다시 태운다.
    @Test
    void 파생본_업로드가_실패해도_예외를_밖으로_내보내지_않는다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(3000, 2000, "jpg"));
        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willThrow(new RuntimeException("R2 가 잠깐 흔들렸다"));

        assertThatNoException()
            .isThrownBy(() -> generateThumbnailService.generate(file.getId()));

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.PROCESSING);
        assertThat(after.getThumbnailKey()).isNull();
        assertThat(after.getPreviewKey()).isNull();
    }

    // 썸네일은 올라갔는데 프리뷰에서 넘어진 경우. 반쪽짜리로 READY 를 찍으면 상세 화면이
    // 영영 프리뷰 없이 굳는다. 둘 다 없는 것으로 보고 통째로 다시 태운다 —
    // 키가 원본에서 유도되므로 다시 태워도 같은 자리에 덮어써서 고아가 남지 않는다.
    @Test
    void 프리뷰에서_실패하면_썸네일까지_확정하지_않고_다시_태운다() {
        StoredFile file = processing("사진.jpg", "image/jpeg");
        givenOriginal(image(3000, 2000, "jpg"));
        given(fileStoragePort.openUploadStream(
            argThat(key -> key != null && key.value().contains("/previews/")), anyString()))
            .willThrow(new RuntimeException("프리뷰만 실패"));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getStatus()).isEqualTo(UploadStatus.PROCESSING);
        assertThat(after.getPreviewKey()).isNull();
    }

    // 위와 짝이 되는 경우다. R2 가 잠깐 흔들린 것까지 READY 로 확정하면 멀쩡한 영상이
    // 썸네일 없이 굳어 손으로 고칠 방법이 없다.
    @Test
    void 일시적인_실패로_프레임을_못_뽑은_영상은_PROCESSING으로_남긴다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        given(videoFrameExtractor.extractFirstFrame(anyString(), anyInt()))
            .willReturn(ExtractionResult.retryLater());

        generateThumbnailService.generate(file.getId());

        assertThat(reload(file).getStatus()).isEqualTo(UploadStatus.PROCESSING);
    }

    // 상세 화면이 사진인지 영상인지 가리지 않고 같은 필드를 읽도록, 사진의 EXIF 와 같은 자리에 담는다.
    @Test
    void 영상의_촬영_시각과_좌표를_저장한다() {
        StoredFile file = processing("영상.mp4", "video/mp4");
        Instant takenAt = Instant.parse("2026-09-20T14:30:00Z");
        GeoPoint location = GeoPoint.ofNullable(
            new BigDecimal("37.566500"), new BigDecimal("126.978000"));
        givenExtractedFrame(new ExtractedFrame(
            1920, 1080, 12, takenAt, location, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getTakenAt()).isEqualTo(takenAt);
        assertThat(after.getLocation()).isEqualTo(location);
    }

    // 컨테이너가 재생시간을 적어두지 않아도 썸네일까지 포기할 이유는 없다.
    @Test
    void 재생시간을_모르는_영상도_썸네일은_만든다() {
        StoredFile file = processing("영상.webm", "video/webm");
        givenExtractedFrame(new ExtractedFrame(1280, 720, null, null, null, image(400, 225, "jpg")));

        generateThumbnailService.generate(file.getId());

        StoredFile after = reload(file);
        assertThat(after.getThumbnailKey()).isNotNull();
        assertThat(after.getDurationSeconds()).isNull();
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
        file.completeProcessing(ProcessedMedia.ofImage(file.getStorageKey().thumbnail("webp"),
            file.getStorageKey().preview("webp"), 100, 100, null, null));
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
        return uploadedAt("/thumbnails/");
    }

    private byte[] uploadedPreview() {
        return uploadedAt("/previews/");
    }

    private byte[] uploadedAt(String prefix) {
        return uploaded.entrySet().stream()
            .filter(entry -> entry.getKey().value().contains(prefix))
            .map(entry -> entry.getValue().toByteArray())
            .findFirst()
            .orElseThrow(() -> new AssertionError(prefix + " 아래로 올라간 것이 없다: "
                + uploaded.keySet()));
    }

    // 원본을 읽을 때마다 새 스트림을 줘야 한다. 같은 스트림을 재사용하면 두 번째 읽기가 빈 값이 된다.
    private void givenOriginal(StorageKey key, byte[] content) {
        given(fileStoragePort.openDownloadStream(key))
            .willAnswer(call -> new ByteArrayInputStream(content));
    }

    private void givenExtractedFrame(ExtractedFrame frame) {
        given(videoFrameExtractor.extractFirstFrame(anyString(), anyInt()))
            .willReturn(ExtractionResult.extracted(frame));
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
