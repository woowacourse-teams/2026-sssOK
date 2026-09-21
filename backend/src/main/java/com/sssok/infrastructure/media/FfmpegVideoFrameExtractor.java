package com.sssok.infrastructure.media;

import com.sssok.application.port.out.VideoFrameExtractorPort;
import com.sssok.domain.file.GeoPoint;
import com.sssok.infrastructure.config.VideoProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// ffmpeg 실행 파일로 영상에서 프레임 한 장을 뽑는다.
//
// 자바 라이브러리(JavaCV 등) 대신 실행 파일을 쓴 이유는 두 가지다.
//  - 자바 의존성이 늘지 않는다.
//  - 깨진 영상 때문에 디코더가 죽어도 별도 프로세스라 서버는 멀쩡하다.
//
// 원본을 내려받지 않는다. 입력이 HTTP 주소면 ffmpeg 가 Range 로 필요한 구간만 읽어,
// 1GB 영상이어도 실제로 오가는 것은 수 MB 다.
@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegVideoFrameExtractor implements VideoFrameExtractorPort {

    private static final byte[] NOTHING = new byte[0];

    // 프로세스가 끝난 뒤 남은 출력을 마저 모으는 시간. 파이프에 남은 것을 비우는 정도라 짧게 둔다.
    private static final long DRAIN_TIMEOUT_MILLIS = 2_000L;

    // ffprobe 가 크기를 알려주지 못하면 뽑은 프레임도 믿을 수 없다고 본다.
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String DURATION = "duration";

    // 컨테이너 메타데이터. ffprobe 는 태그 키 앞에 TAG: 를 붙여 내놓는다.
    private static final String CREATION_TIME = "TAG:creation_time";
    private static final String LOCATION = "TAG:location";
    // 아이폰은 같은 좌표를 애플 전용 키에도 적는다. 기기에 따라 둘 중 하나만 있다.
    private static final String APPLE_LOCATION = "TAG:com.apple.quicktime.location.ISO6709";

    // ISO 6709 좌표. "+37.5665+126.9780/" 또는 고도까지 붙은 "+37.5665+126.9780+050.000/" 형태다.
    private static final Pattern ISO6709 =
        Pattern.compile("^([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)");

    // 소수점 6자리면 약 10cm 단위다. 컬럼 정의(NUMERIC(9,6))와 사진 경로에 맞춘다.
    private static final int COORDINATE_SCALE = 6;

    private final VideoProperties properties;

    @Override
    public Optional<ExtractedFrame> extractFirstFrame(String sourceUrl, int maxWidth) {
        try {
            Map<String, String> probed = probe(sourceUrl);
            Integer width = intOrNull(probed.get(WIDTH));
            Integer height = intOrNull(probed.get(HEIGHT));
            if (width == null || height == null) {
                return Optional.empty();
            }

            byte[] frame = grabFrame(sourceUrl, maxWidth);
            if (frame.length == 0) {
                return Optional.empty();
            }

            // 뽑힌 바이트가 정말 이미지인지 여기서 확인한다. 회전 보정에 쓸 방향도 같이 읽는다.
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(frame));
            if (decoded == null) {
                return Optional.empty();
            }

            return Optional.of(new ExtractedFrame(
                displayWidth(width, height, decoded),
                displayHeight(width, height, decoded),
                durationSeconds(probed.get(DURATION)),
                takenAt(probed.get(CREATION_TIME)),
                location(probed),
                frame));
        } catch (IOException e) {
            // ffmpeg 가 깔려 있지 않은 환경(로컬 개발 등)도 여기로 온다. 썸네일만 없을 뿐,
            // 영상 자체는 완료 처리되어 원본 재생·다운로드는 그대로 된다.
            log.warn("영상 프레임 추출기를 실행하지 못했습니다.", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    // -ss 를 -i 앞에 두는 게 핵심이다. 그래야 앞부분을 디코딩하지 않고 건너뛴다.
    //
    // 지정한 지점이 영상 끝보다 뒤면 ffmpeg 는 아무것도 내놓지 않는다. 1초가 안 되는 짧은 영상이
    // 여기에 걸리므로, 빈손으로 돌아오면 맨 앞에서 한 번 더 집는다.
    private byte[] grabFrame(String sourceUrl, int maxWidth)
        throws IOException, InterruptedException {
        byte[] frame = runFfmpeg(sourceUrl, maxWidth, properties.frameAt());
        if (frame.length > 0) {
            return frame;
        }
        return runFfmpeg(sourceUrl, maxWidth, Duration.ZERO);
    }

    private byte[] runFfmpeg(String sourceUrl, int maxWidth, Duration frameAt)
        throws IOException, InterruptedException {
        return run(
            "ffmpeg",
            // 표준 입력을 붙잡고 기다리지 않게 한다. 워커 스레드에는 넣어줄 입력이 없다.
            "-nostdin",
            "-v", "error",
            "-ss", seconds(frameAt),
            "-i", sourceUrl,
            "-frames:v", "1",
            // 원본이 이미 작으면 늘리지 않는다. 확대한 썸네일은 원본보다 크면서 더 흐리다.
            // min() 안의 쉼표는 필터 구분자와 겹쳐, 작은따옴표로 묶어 ffmpeg 에게 넘긴다.
            "-vf", "scale='min(iw," + maxWidth + ")':-2",
            "-an",
            // JPEG 가 쓰는 full-range 로 못 박는다. 비워 두면 뽑을 프레임이 없을 때
            // mjpeg 인코더가 색 범위를 트집 잡아, 진짜 원인을 가리는 오류를 남긴다.
            "-pix_fmt", "yuvj420p",
            "-f", "image2",
            "-c:v", "mjpeg",
            "pipe:1");
    }

    // 원본 크기와 재생시간은 헤더만 읽으면 나온다. 프레임을 뽑기 전에 불러, 읽을 수 없는 파일이면
    // 비싼 디코딩까지 가지 않고 끝낸다.
    private Map<String, String> probe(String sourceUrl) throws IOException, InterruptedException {
        byte[] output = run(
            "ffprobe",
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=width,height:format=duration"
                + ":format_tags=creation_time,location,com.apple.quicktime.location.ISO6709",
            "-of", "default=noprint_wrappers=1:nokey=0",
            sourceUrl);
        return parse(new String(output, StandardCharsets.UTF_8));
    }

    private Map<String, String> parse(String output) {
        Map<String, String> values = new HashMap<>();
        for (String line : output.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).trim(),
                    line.substring(separator + 1).trim());
            }
        }
        return values;
    }

    // 외부 프로그램을 실행하는 자리라, 아래 세 가지를 빠뜨리면 워커 스레드가 영영 묶인다.
    //  - 인자를 배열로 넘긴다. 서명 URL 의 & 와 ? 가 셸을 거치면 주소가 잘린다.
    //  - stdout 과 stderr 를 각각 다른 스레드에서 비운다. 한쪽만 읽으면 파이프 버퍼가 차서
    //    ffmpeg 가 멈춘 채로 대기한다. redirectErrorStream 은 쓸 수 없다 — 프레임 바이트에
    //    로그가 섞인다.
    //  - 시간이 지나면 반드시 죽인다. 남겨두면 좀비가 쌓여 컨테이너가 죽는다.
    private byte[] run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        try {
            StreamCollector stdout = StreamCollector.start(process.getInputStream());
            StreamCollector stderr = StreamCollector.start(process.getErrorStream());

            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("영상 처리가 {} 안에 끝나지 않아 중단합니다. command={}",
                    properties.timeout(), command[0]);
                return NOTHING;
            }
            if (process.exitValue() != 0) {
                log.warn("영상 처리가 실패했습니다. command={} exitCode={} stderr={}",
                    command[0], process.exitValue(), stderr.text());
                return NOTHING;
            }
            return stdout.bytes();
        } finally {
            // 정상 종료했으면 아무 일도 하지 않는다. 시간이 넘었거나 예외로 빠져나가는 길에서만
            // 실제로 죽이는데, 이 한 줄이 좀비를 막는 유일한 지점이다.
            process.destroyForcibly();
        }
    }

    // ffmpeg 는 회전 정보를 반영해 프레임을 뽑지만, ffprobe 가 알려주는 크기는 회전 전 값이다.
    // 세로로 찍은 영상이 가로 크기로 저장되지 않도록, 뽑힌 프레임의 방향에 맞춰 뒤집는다.
    private int displayWidth(int probedWidth, int probedHeight, BufferedImage frame) {
        return isRotated(probedWidth, probedHeight, frame) ? probedHeight : probedWidth;
    }

    private int displayHeight(int probedWidth, int probedHeight, BufferedImage frame) {
        return isRotated(probedWidth, probedHeight, frame) ? probedWidth : probedHeight;
    }

    private boolean isRotated(int probedWidth, int probedHeight, BufferedImage frame) {
        return isPortrait(frame.getWidth(), frame.getHeight()) != isPortrait(probedWidth, probedHeight);
    }

    private boolean isPortrait(int width, int height) {
        return height > width;
    }

    // 컨테이너가 재생시간을 적어두지 않았으면 "N/A" 가 오거나 아예 없다.
    // 1초가 안 되는 영상도 0초로 보여주지 않고 1초로 올린다.
    private Integer durationSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(value);
            return seconds <= 0 ? null : (int) Math.max(1, Math.round(seconds));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 사진의 EXIF 촬영 시각에 해당한다. 영상은 시간대까지 적어 두므로 그대로 읽으면 된다.
    // 기기에 따라 "0000-00-00T00:00:00Z" 같은 빈 값을 적어두기도 해서, 못 읽으면 비운다.
    private Instant takenAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // 안드로이드는 location 에, 아이폰은 애플 전용 키에 적는다. 있는 쪽을 쓴다.
    private GeoPoint location(Map<String, String> probed) {
        String value = probed.getOrDefault(LOCATION, probed.get(APPLE_LOCATION));
        if (value == null) {
            return null;
        }
        Matcher matcher = ISO6709.matcher(value.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return GeoPoint.ofNullable(
                new BigDecimal(matcher.group(1)).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                new BigDecimal(matcher.group(2)).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private Integer intOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ffmpeg 의 -ss 는 초 단위 실수를 받는다. Duration.toString() 의 PT1S 형식은 알아듣지 못한다.
    private String seconds(Duration duration) {
        return String.valueOf(duration.toMillis() / 1000.0);
    }

    // 프로세스 출력 한쪽을 통째로 받아두는 스레드. 읽는 쪽과 기다리는 쪽을 나눠야
    // 출력이 파이프 버퍼보다 클 때 서로 막히지 않는다.
    private static final class StreamCollector {

        private final Thread thread;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(InputStream source) {
            this.thread = new Thread(() -> collect(source), "ffmpeg-drain");
            this.thread.setDaemon(true);
        }

        private static StreamCollector start(InputStream source) {
            StreamCollector collector = new StreamCollector(source);
            collector.thread.start();
            return collector;
        }

        private void collect(InputStream source) {
            try (source) {
                source.transferTo(buffer);
            } catch (IOException e) {
                // 프로세스를 강제로 죽이면 읽던 스트림이 끊긴다. 모아둔 만큼만 쓰고 끝낸다.
            }
        }

        private byte[] bytes() throws InterruptedException {
            thread.join(DRAIN_TIMEOUT_MILLIS);
            return buffer.toByteArray();
        }

        private String text() throws InterruptedException {
            return new String(bytes(), StandardCharsets.UTF_8);
        }
    }
}
