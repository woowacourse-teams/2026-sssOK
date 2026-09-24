package com.sssok.benchmark;

import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

// 벤치마크가 견주는 세 가지 인코딩 경로. 프로덕션 코드가 아니라 측정용이라 test 소스셋에 둔다.
//
// JPEG·WebP 는 ImageIO 안에서 끝나고, AVIF 만 ffmpeg 을 띄운다 — JVM 에서 쓸 만한 AVIF
// 인코더가 없다. 그 프로세스 기동 비용까지가 AVIF 를 고를 때 실제로 치르는 값이라, 일부러
// 빼지 않고 같이 잰다.
final class DerivativeCodec {

    private DerivativeCodec() {
    }

    static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        return encodeWithImageIo("jpg", image, quality, null);
    }

    static byte[] encodeWebp(BufferedImage image, float quality) throws IOException {
        return encodeWithImageIo("webp", image, quality, "Lossy");
    }

    private static byte[] encodeWithImageIo(String format, BufferedImage image, float quality,
                                            String compressionType) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ImageWriteParam param = "webp".equals(format)
            ? new WebPWriteParam(Locale.getDefault())
            : writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        if (compressionType != null) {
            param.setCompressionType(compressionType);
        }
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            // JPEG 은 알파 채널을 못 담는다. 벤치마크 샘플은 전부 사진이라 TYPE_INT_RGB 로 맞춘다.
            writer.write(null, new IIOImage(toRgb(image), null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // 입력 픽셀은 파이프로 바로 밀어 넣는다. PNG 로 한 번 적었다 읽으면 재는 값에 PNG 인코딩
    // 시간이 섞여, AVIF 가 실제보다 느려 보인다.
    //
    // 출력만은 임시 파일을 거친다 — AVIF 는 컨테이너 헤더를 나중에 되돌아가 채우는 구조라
    // ffmpeg 의 avif 먹서가 "muxer does not support non seekable output" 으로 거절한다.
    // 파생본은 수십 KB 라 이 왕복 자체는 밀리초 수준이고, 실제로 도입해도 똑같이 치를 비용이다.
    static byte[] encodeAvif(BufferedImage image, int crf) throws IOException {
        BufferedImage rgb = toRgb(image);
        Path temp = Files.createTempFile("benchmark-", ".avif");
        try {
            runFfmpeg(List.of("ffmpeg", "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgb24",
                "-s", rgb.getWidth() + "x" + rgb.getHeight(), "-i", "-",
                "-c:v", "libsvtav1", "-crf", String.valueOf(crf), "-frames:v", "1",
                "-pix_fmt", "yuv420p", "-f", "avif", temp.toString()), rawRgb(rgb));
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static BufferedImage decodeAvif(byte[] avif, int width, int height) throws IOException {
        Path temp = Files.createTempFile("benchmark-", ".avif");
        try {
            Files.write(temp, avif);
            byte[] raw = runFfmpeg(List.of("ffmpeg", "-y", "-loglevel", "error",
                "-i", temp.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"), new byte[0]);
            return fromRawRgb(raw, width, height);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ffmpeg 은 stdout 을 읽어 주지 않으면 파이프가 차서 멈춘다. 쓰기와 읽기를 다른 스레드로 나눈다.
    private static byte[] runFfmpeg(List<String> command, byte[] input) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.to(Path.of("/dev/null").toFile()))
            .start();
        Thread writer = Thread.ofVirtual().start(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(input);
            } catch (IOException ignored) {
                // 인코더가 먼저 끝나 파이프가 닫힌 경우. 아래에서 종료 코드로 판정한다.
            }
        });
        byte[] output;
        try (InputStream stdout = process.getInputStream()) {
            output = stdout.readAllBytes();
        }
        try {
            writer.join();
            if (process.waitFor() != 0) {
                throw new IOException("ffmpeg 실패: " + command);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return output;
    }

    static BufferedImage decode(byte[] encoded) throws IOException {
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(encoded));
        if (decoded == null) {
            throw new IOException("디코딩할 수 없는 이미지다.");
        }
        return toRgb(decoded);
    }

    static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage converted = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        converted.createGraphics().drawImage(image, 0, 0, null);
        return converted;
    }

    private static byte[] rawRgb(BufferedImage image) {
        byte[] raw = new byte[image.getWidth() * image.getHeight() * 3];
        int index = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                raw[index++] = (byte) ((rgb >> 16) & 0xFF);
                raw[index++] = (byte) ((rgb >> 8) & 0xFF);
                raw[index++] = (byte) (rgb & 0xFF);
            }
        }
        return raw;
    }

    private static BufferedImage fromRawRgb(byte[] raw, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = raw[index++] & 0xFF;
                int green = raw[index++] & 0xFF;
                int blue = raw[index++] & 0xFF;
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    static byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
