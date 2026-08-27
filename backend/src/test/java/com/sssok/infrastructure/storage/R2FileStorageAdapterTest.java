package com.sssok.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.FileStoragePort.UploadedObject;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.R2Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

// 실제 R2 에 붙어 서명 URL 과 HeadObject 가 동작하는지 확인한다.
// 자격증명이 없으면 조용히 건너뛰므로 CI 는 이 테스트 없이도 통과한다.
// 자격증명은 backend/.env 에 두며, 재현 방법은 docs/backend/R2_PRESIGNED_UPLOAD.md 에 있다.
@EnabledIf("hasCredentials")
class R2FileStorageAdapterTest {

    private static final Map<String, String> CONFIG = loadConfig();
    private static final byte[] BODY = {(byte) 0x89, 'P', 'N', 'G'};

    private StorageKey uploaded;

    static boolean hasCredentials() {
        return List.of("R2_ENDPOINT", "R2_ACCESS_KEY", "R2_SECRET_KEY", "R2_BUCKET").stream()
            .allMatch(key -> CONFIG.get(key) != null && !CONFIG.get(key).isBlank());
    }

    private R2FileStorageAdapter adapter() {
        return new R2FileStorageAdapter(new R2Properties(
            CONFIG.get("R2_ENDPOINT"), CONFIG.get("R2_ACCESS_KEY"), CONFIG.get("R2_SECRET_KEY"),
            CONFIG.get("R2_BUCKET"), CONFIG.get("R2_PUBLIC_BASE_URL")));
    }

    @AfterEach
    void cleanUp() {
        if (uploaded != null) {
            adapter().delete(uploaded);
            uploaded = null;
        }
    }

    private int put(String url, String contentType) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(BODY));
            if (contentType != null) {
                request.header("Content-Type", contentType);
            }
            HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 발급한_URL_로_올리고_올라온_것을_되읽는다() {
        R2FileStorageAdapter adapter = adapter();
        StorageKey key = StorageKey.generate(1L, MediaType.PNG);
        String url = adapter.presignPut(key, "image/png", Duration.ofMinutes(10));

        assertThat(put(url, "image/png")).isEqualTo(200);
        uploaded = key;

        Optional<UploadedObject> found = adapter.findUploaded(key);
        assertThat(found).isPresent();
        assertThat(found.get().sizeBytes()).isEqualTo(BODY.length);
        assertThat(found.get().contentType()).isEqualTo("image/png");
    }

    @Test
    void 서명한_것과_다른_ContentType_은_거부된다() {
        StorageKey key = StorageKey.generate(1L, MediaType.PNG);
        String url = adapter().presignPut(key, "image/png", Duration.ofMinutes(10));

        // Content-Type 이 서명 대상이라 값이 다르거나 빠지면 통과하지 못한다.
        assertThat(put(url, "application/octet-stream")).isEqualTo(403);
        assertThat(put(url, null)).isEqualTo(403);
    }

    @Test
    void 발급한_다운로드_URL_로_받으면_지정한_응답_헤더가_그대로_내려온다() {
        R2FileStorageAdapter adapter = adapter();
        StorageKey key = StorageKey.generate(1L, MediaType.PNG);
        String putUrl = adapter.presignPut(key, "image/png", Duration.ofMinutes(10));
        assertThat(put(putUrl, "image/png")).isEqualTo(200);
        uploaded = key;

        String disposition = "attachment; filename=\"IMG_0421.png\"; filename*=UTF-8''IMG_0421.png";
        String url = adapter.presignGet(key, disposition, "image/png", Duration.ofMinutes(5));

        HttpResponse<byte[]> response = get(url);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-disposition")).contains(disposition);
        assertThat(response.headers().firstValue("content-type")).contains("image/png");
    }

    @Test
    void 올린_파일을_스트림으로_그대로_읽는다() throws IOException {
        R2FileStorageAdapter adapter = adapter();
        StorageKey key = StorageKey.generate(1L, MediaType.PNG);
        String putUrl = adapter.presignPut(key, "image/png", Duration.ofMinutes(10));
        assertThat(put(putUrl, "image/png")).isEqualTo(200);
        uploaded = key;

        try (InputStream in = adapter.openDownloadStream(key)) {
            assertThat(in.readAllBytes()).isEqualTo(BODY);
        }
    }

    @Test
    void 올리지_않은_키는_비어_있다() {
        Optional<UploadedObject> found =
            adapter().findUploaded(StorageKey.generate(1L, MediaType.PNG));

        // 완료 등록이 이 결과로 위조를 걸러낸다.
        assertThat(found).isEmpty();
    }

    @Test
    void 없는_키를_지워도_실패하지_않는다() {
        // 정리 배치가 다시 돌아도 안전해야 한다.
        adapter().delete(StorageKey.generate(1L, MediaType.PNG));
    }

    private HttpResponse<byte[]> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

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
        for (String key : List.of("R2_ENDPOINT", "R2_ACCESS_KEY", "R2_SECRET_KEY",
            "R2_BUCKET", "R2_PUBLIC_BASE_URL")) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                config.put(key, value);
            }
        }
        return config;
    }
}
