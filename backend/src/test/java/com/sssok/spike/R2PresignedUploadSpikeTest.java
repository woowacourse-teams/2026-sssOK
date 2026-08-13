package com.sssok.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 이슈 #16 스파이크. presigned PUT 방식으로 이미지 파일이 Cloudflare R2에 실제로 올라가는지 확인한다.
 * 버리는 코드이므로 프로덕션 구조(FileStoragePort/R2FileStorageAdapter)에 맞추지 않았다.
 *
 * 설정은 OS 환경변수를 먼저 보고, 없으면 backend/.env 를 직접 읽는다.
 * 자격증명이 없으면 테스트는 조용히 skip 된다.
 */
@EnabledIf("credentialsAvailable")
class R2PresignedUploadSpikeTest {

    private static final Map<String, String> CONFIG = loadConfig();

    private static final String ENDPOINT = CONFIG.get("R2_ENDPOINT");
    private static final String ACCESS_KEY = CONFIG.get("R2_ACCESS_KEY");
    private static final String SECRET_KEY = CONFIG.get("R2_SECRET_KEY");
    private static final String BUCKET = CONFIG.getOrDefault("R2_BUCKET", "sssok-dev");

    private static final String CONTENT_TYPE = "image/png";
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final Duration TTL = Duration.ofMinutes(10);

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static boolean credentialsAvailable() {
        return ACCESS_KEY != null && !ACCESS_KEY.isBlank();
    }

    // 발급 -> 이미지 PUT 업로드 -> presigned GET 으로 되읽어 이미지로 디코딩까지 확인.
    @Test
    void presignedPutUploadsImageToR2() throws Exception {
        byte[] original = pngBytes();
        String key = "spike/" + Instant.now().toEpochMilli() + ".png";

        try (S3Presigner presigner = presigner()) {
            URI uploadUrl = presignPut(presigner, key, CONTENT_TYPE);

            HttpResponse<String> uploaded = put(uploadUrl, CONTENT_TYPE, original);
            assertThat(uploaded.statusCode()).isEqualTo(200);

            HttpResponse<byte[]> downloaded = HTTP.send(
                    HttpRequest.newBuilder(presignGet(presigner, key)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(downloaded.statusCode()).isEqualTo(200);
            // R2가 업로드 시 지정한 Content-Type 을 그대로 보관하는지.
            assertThat(downloaded.headers().firstValue("content-type")).hasValue(CONTENT_TYPE);
            // 바이너리가 한 바이트도 변형되지 않았는지.
            assertThat(downloaded.body()).isEqualTo(original);

            // 받은 바이트가 실제로 이미지로 열리는지 (깨진 파일이 아닌지).
            BufferedImage roundTripped = ImageIO.read(new ByteArrayInputStream(downloaded.body()));
            assertThat(roundTripped).isNotNull();
            assertThat(roundTripped.getWidth()).isEqualTo(WIDTH);
            assertThat(roundTripped.getHeight()).isEqualTo(HEIGHT);

            Path saved = Path.of("build", "spike-downloaded.png");
            Files.createDirectories(saved.getParent());
            Files.write(saved, downloaded.body());

            System.out.println("[spike] 업로드 키     = " + key);
            System.out.println("[spike] 크기          = " + original.length + " bytes, "
                    + roundTripped.getWidth() + "x" + roundTripped.getHeight());
            System.out.println("[spike] 내려받은 파일 = " + saved.toAbsolutePath());
        }
    }

    // 서명에 포함한 Content-Type 과 실제 PUT 헤더가 다르면 R2가 서명을 거부한다.
    // 프론트에 업로드 URL을 내려줄 때 Content-Type을 같이 약속해야 하는 이유.
    @Test
    void contentTypeMismatchIsRejected() throws Exception {
        String key = "spike/mismatch-" + Instant.now().toEpochMilli() + ".png";

        try (S3Presigner presigner = presigner()) {
            URI uploadUrl = presignPut(presigner, key, CONTENT_TYPE);

            HttpResponse<String> response = put(uploadUrl, "application/octet-stream", pngBytes());

            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    // 외부 파일에 의존하지 않도록 테스트가 직접 PNG를 그린다.
    private static byte[] pngBytes() {
        System.setProperty("java.awt.headless", "true");

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x2E6FF6));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 32));
        graphics.drawString("sssOK #16", 40, 100);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // 셸 export 없이도 돌아가도록 .env 를 직접 읽는다. OS 환경변수가 있으면 그쪽이 우선.
    private static Map<String, String> loadConfig() {
        Map<String, String> config = new HashMap<>();

        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator > 0) {
                        config.put(trimmed.substring(0, separator).trim(),
                                trimmed.substring(separator + 1).trim());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        for (String key : List.of("R2_ENDPOINT", "R2_ACCESS_KEY", "R2_SECRET_KEY", "R2_BUCKET")) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                config.put(key, value);
            }
        }
        return config;
    }

    private S3Presigner presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(ENDPOINT))
                // R2는 리전 개념이 없어 'auto' 고정. 실제 리전 값을 넣으면 서명이 깨진다.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                // 가상 호스트 방식(bucket.<account>.r2.cloudflarestorage.com) 대신 path-style 사용.
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private URI presignPut(S3Presigner presigner, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .contentType(contentType)
                .build();

        return URI.create(presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(TTL)
                        .putObjectRequest(request)
                        .build())
                .url()
                .toString());
    }

    private URI presignGet(S3Presigner presigner, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .build();

        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(TTL)
                        .getObjectRequest(request)
                        .build())
                .url()
                .toString());
    }

    private HttpResponse<String> put(URI url, String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(url)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
