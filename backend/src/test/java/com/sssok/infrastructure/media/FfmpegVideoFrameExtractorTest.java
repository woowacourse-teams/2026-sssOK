package com.sssok.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractedFrame;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractionResult;
import com.sssok.domain.file.GeoPoint;
import com.sssok.infrastructure.config.VideoProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// 실물 ffmpeg 로 검증한다. 프로세스를 다루는 코드라 목으로는 정작 확인하고 싶은 것
// (좀비가 남는지, 출력 버퍼가 막히는지)을 볼 수 없다.
//
// ffmpeg 가 없는 환경에서는 통째로 건너뛴다. 운영 이미지에는 깔려 있고(Dockerfile),
// 없을 때 썸네일만 비는 것은 FfmpegVideoFrameExtractor 가 이미 감당한다.
class FfmpegVideoFrameExtractorTest {

    private static final int MAX_WIDTH = 400;

    @TempDir
    Path directory;

    @BeforeAll
    static void requireFfmpeg() {
        boolean available = installed("ffmpeg") && installed("ffprobe");
        // CI 에서는 건너뛰지 않는다. 조용히 스킵되면 추출기가 깨진 채로 머지돼도
        // 초록불이 뜬다 — 설치 스텝이 빠지면 여기서 터뜨린다 (.github/workflows/backend-ci.yml).
        if (System.getenv("CI") != null) {
            assertThat(available)
                .as("CI 에는 ffmpeg 가 깔려 있어야 한다. 워크플로의 ffmpeg 설치 스텝을 확인하라")
                .isTrue();
        }
        assumeTrue(available, "ffmpeg 가 설치되어 있지 않다");
    }

    @Test
    void 영상에서_프레임을_뽑는다() {
        Path video = video("영상.mp4", 640, 480, 3);

        ExtractedFrame frame = extract(video).frame();

        assertThat(readable(frame.content())).isTrue();
    }

    // 클라이언트가 자리를 미리 잡는 데 쓰는 값이라, 썸네일이 아니라 원본의 크기여야 한다.
    @Test
    void 썸네일이_아니라_원본_영상의_크기를_알려준다() {
        Path video = video("영상.mp4", 640, 480, 3);

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.width()).isEqualTo(640);
        assertThat(frame.height()).isEqualTo(480);
    }

    @Test
    void 뽑은_프레임은_지정한_너비로_줄어든다() {
        Path video = video("영상.mp4", 640, 480, 3);

        ExtractedFrame frame = extract(video).frame();

        assertThat(decode(frame.content()).getWidth()).isEqualTo(MAX_WIDTH);
    }

    // 확대한 썸네일은 원본보다 크면서 더 흐리기만 하다. 이미지 경로와 같은 규칙이다.
    @Test
    void 원본이_이미_작으면_늘리지_않는다() {
        Path video = video("작은영상.mp4", 320, 240, 2);

        ExtractedFrame frame = extract(video).frame();

        assertThat(decode(frame.content()).getWidth()).isEqualTo(320);
    }

    @Test
    void 재생시간을_읽어온다() {
        Path video = video("영상.mp4", 640, 480, 3);

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.durationSeconds()).isEqualTo(3);
    }

    @Test
    void 세로로_찍은_영상은_세로_프레임이_된다() {
        Path video = video("세로영상.mp4", 480, 640, 2);

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.width()).isEqualTo(480);
        assertThat(frame.height()).isEqualTo(640);
        assertThat(decode(frame.content()).getHeight())
            .isGreaterThan(decode(frame.content()).getWidth());
    }

    // 아이폰이 내놓는 형태. 컨테이너가 mp4 가 아니라 quicktime 이다.
    @Test
    void 아이폰_mov_에서_프레임을_뽑는다() {
        Path video = encoded("아이폰.mov", "libx264");

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.width()).isEqualTo(320);
        assertThat(readable(frame.content())).isTrue();
    }

    @Test
    void webm_에서_프레임을_뽑는다() {
        Path video = encoded("영상.webm", "libvpx-vp9");

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.width()).isEqualTo(320);
        assertThat(readable(frame.content())).isTrue();
    }

    // 아이폰으로 세로로 찍은 영상이 이 모양이다. 가로로 저장해 두고 "90도 돌려서 보여라"
    // 라고 적어 둔다. ffprobe 는 돌리기 전 크기를 알려주므로 그대로 믿으면 눕는다.
    @Test
    void 회전_정보가_붙은_영상은_돌린_뒤의_크기를_알려준다() {
        Path video = rotated("아이폰세로.mp4", 640, 480);

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.width()).isEqualTo(480);
        assertThat(frame.height()).isEqualTo(640);
    }

    // 1초 지점을 집는데 영상이 그보다 짧으면 ffmpeg 는 빈손으로 돌아온다. 맨 앞에서 다시 집어야 한다.
    @Test
    void 지정한_지점보다_짧은_영상도_썸네일을_만든다() {
        Path video = video("아주짧은영상.mp4", 640, 480, Duration.ofMillis(400));

        ExtractedFrame frame = extract(video).frame();

        assertThat(readable(frame.content())).isTrue();
    }

    // 사진의 EXIF 에 해당한다. 상세 화면이 사진과 같은 필드를 읽을 수 있어야 한다.
    @Test
    void 촬영_시각을_읽어온다() {
        Path video = tagged("찍은영상.mp4",
            "creation_time=2026-09-20T14:30:00.000000Z");

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.takenAt()).isEqualTo(Instant.parse("2026-09-20T14:30:00Z"));
    }

    // 안드로이드가 적는 자리. ISO 6709 문자열에서 위도·경도를 끊어 읽는다.
    @Test
    void 촬영_좌표를_읽어온다() {
        Path video = tagged("위치있는영상.mp4", "location=+37.5665+126.9780/");

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.location()).isEqualTo(GeoPoint.ofNullable(
            new BigDecimal("37.566500"), new BigDecimal("126.978000")));
    }

    // 고도까지 붙는 기기가 있다. 뒤에 뭐가 더 붙어도 앞의 두 값만 읽으면 된다.
    @Test
    void 고도가_붙은_좌표도_읽어온다() {
        Path video = tagged("고도있는영상.mp4", "location=+37.5665+126.9780+050.000/");

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.location()).isEqualTo(GeoPoint.ofNullable(
            new BigDecimal("37.566500"), new BigDecimal("126.978000")));
    }

    // 위치 기록을 꺼두고 찍었거나 편집하며 지워진 영상이다. 없다고 썸네일까지 포기하지 않는다.
    @Test
    void 촬영_정보가_없어도_썸네일은_만든다() {
        Path video = video("민짜영상.mp4", 320, 240, 2);

        ExtractedFrame frame = extract(video).frame();

        assertThat(frame.location()).isNull();
        assertThat(readable(frame.content())).isTrue();
    }

    // 예외를 던지면 영상 하나 때문에 워커가 멈춘다. 빈 값으로 돌려 썸네일만 비운다.
    @Test
    void 깨진_영상이면_비어_있다() {
        Path broken = write("깨진영상.mp4", "이건 영상이 아니다".getBytes());

        ExtractionResult result = extract(broken);

        assertThat(result.hasFrame()).isFalse();
        // 다시 태워도 같은 결과라, 호출부가 썸네일 없이 완료로 넘길 수 있어야 한다.
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void 없는_원본이면_비어_있다() {
        ExtractionResult result = extract(directory.resolve("없는영상.mp4"));

        assertThat(result.hasFrame()).isFalse();
        assertThat(result.retryable()).isFalse();
    }

    // 죽이지 않으면 좀비가 쌓여 컨테이너가 죽는다. 제한 시간 안에 돌아오는 것으로 확인한다.
    // 깨진 영상과 달리 retryable 인 이유: 시간이 넘은 것은 영상이 깨졌다는 뜻이 아니다.
    @Test
    void 제한_시간을_넘기면_다시_시도할_수_있게_알린다() {
        Path video = video("영상.mp4", 640, 480, 3);
        FfmpegVideoFrameExtractor extractor = extractor(Duration.ofMillis(1));

        long startedAt = System.nanoTime();
        ExtractionResult result = extractor.extractFirstFrame(video.toString(), MAX_WIDTH);
        long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);

        assertThat(result.retryable()).isTrue();
        assertThat(elapsed).isLessThan(10);
    }

    // 원본에 닿지 못한 것은 영상이 깨진 것과 다르다. 서명 URL 을 새로 받아 다시 태우면 살아난다.
    @Test
    void 원본을_읽지_못하면_다시_시도할_수_있게_알린다() {
        // 닫혀 있는 포트라 ffprobe 가 곧바로 연결에 실패한다 — 네트워크로 나가지 않는다.
        String unreachable = "http://127.0.0.1:1/없는영상.mp4";

        ExtractionResult result = extractor(Duration.ofSeconds(20))
            .extractFirstFrame(unreachable, MAX_WIDTH);

        assertThat(result.retryable()).isTrue();
    }

    private ExtractionResult extract(Path video) {
        return extractor(Duration.ofSeconds(20)).extractFirstFrame(video.toString(), MAX_WIDTH);
    }

    private FfmpegVideoFrameExtractor extractor(Duration timeout) {
        return new FfmpegVideoFrameExtractor(
            new VideoProperties(Duration.ofSeconds(1), timeout, MAX_WIDTH, Duration.ofMinutes(10)));
    }

    private Path video(String fileName, int width, int height, int seconds) {
        return video(fileName, width, height, Duration.ofSeconds(seconds));
    }

    // ffmpeg 가 만들어 주는 테스트 패턴으로 진짜 mp4 를 굽는다. 바이너리를 저장소에 넣지 않아도 된다.
    private Path video(String fileName, int width, int height, Duration length) {
        Path target = directory.resolve(fileName);
        run("ffmpeg", "-v", "error", "-y",
            "-f", "lavfi",
            "-i", "testsrc=size=%dx%d:rate=15".formatted(width, height),
            "-t", String.valueOf(length.toMillis() / 1000.0),
            "-pix_fmt", "yuv420p",
            target.toString());
        return target;
    }

    // 확장자로 컨테이너가, codec 으로 코덱이 정해진다. testsrc 는 lavfi 가 만들어 주는 패턴이라
    // 저장소에 실제 영상 파일을 넣지 않아도 된다.
    private Path encoded(String fileName, String codec) {
        Path target = directory.resolve(fileName);
        run("ffmpeg", "-v", "error", "-y",
            "-f", "lavfi",
            "-i", "testsrc=size=320x240:rate=15",
            "-t", "2",
            "-c:v", codec,
            "-pix_fmt", "yuv420p",
            target.toString());
        return target;
    }

    // 가로로 찍힌 영상에 "90도 돌려서 보여라"만 적어 둔다. 픽셀은 그대로다.
    private Path rotated(String fileName, int width, int height) {
        Path source = video("가로원본.mp4", width, height, 2);
        Path target = directory.resolve(fileName);
        run("ffmpeg", "-v", "error", "-y",
            "-display_rotation", "90",
            "-i", source.toString(),
            "-c", "copy",
            target.toString());
        return target;
    }

    // 기기가 컨테이너에 남기는 메타데이터를 그대로 새겨 넣는다.
    private Path tagged(String fileName, String metadata) {
        Path target = directory.resolve(fileName);
        run("ffmpeg", "-v", "error", "-y",
            "-f", "lavfi",
            "-i", "testsrc=size=320x240:rate=15",
            "-t", "1",
            "-pix_fmt", "yuv420p",
            "-metadata", metadata,
            target.toString());
        return target;
    }

    private Path write(String fileName, byte[] content) {
        try {
            return Files.write(directory.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean readable(byte[] image) {
        return decode(image) != null;
    }

    private BufferedImage decode(byte[] image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean installed(String command) {
        try {
            return run(command, "-version") == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("명령이 끝나지 않았습니다: " + command[0]);
            }
            return process.exitValue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
