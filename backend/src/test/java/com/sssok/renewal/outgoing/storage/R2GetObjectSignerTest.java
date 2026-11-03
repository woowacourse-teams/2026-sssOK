package com.sssok.renewal.outgoing.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.domain.download.DownloadFileNames;
import com.sssok.renewal.outgoing.config.R2Properties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// 직접 서명한 URL 이 S3Presigner 가 같은 시각에 만드는 URL 과 글자 단위로 같은지 고정한다.
// presigner 는 R2FileStorage 가 쓰던 설정(path-style, region auto, 고정 자격증명)과 같게 만든다.
// SDK 를 올려 presigner 의 서명 규칙이 바뀌면 이 테스트가 먼저 깨진다 — 그때는 직접 서명 쪽을 맞춘다.
class R2GetObjectSignerTest {

    private static final R2Properties PROPERTIES = new R2Properties(
        "https://account-id.r2.cloudflarestorage.com", "test-access-key", "test-secret-key", "sssok-dev", null);
    private static final DateTimeFormatter AMZ_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final S3Presigner presigner = S3Presigner.builder()
        .endpointOverride(URI.create(PROPERTIES.endpoint()))
        .region(Region.of("auto"))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(PROPERTIES.accessKey(), PROPERTIES.secretKey())))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();

    @AfterEach
    void close() {
        presigner.close();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "a.jpg            | image/jpeg",
        "제주 바다.jpg     | image/jpeg",
        "a (1).jpg        | image/jpeg",
        "quo\"te.png      | image/png",
        "a+b%25&c=d.jpg   | image/jpeg",
        "😀 사진.heic      | image/heic",
        "../../etc/passwd | application/octet-stream",
        "영상 (2).MOV      | video/quicktime",
    })
    void 다운로드_파일명으로_서명한_URL이_presigner와_같다(String fileName, String contentType) {
        StorageKey key = new StorageKey("rooms/1/33db0976-a938-4068-99a0-fc4272206eb4.jpg");

        assertSameAsPresigner(key, DownloadFileNames.contentDispositionOf(fileName), contentType,
            Duration.ofMinutes(5));
    }

    @ParameterizedTest
    @CsvSource({
        "rooms/1/33db0976-a938-4068-99a0-fc4272206eb4.png",
        "thumbnails/rooms/1/33db0976-a938-4068-99a0-fc4272206eb4.webp",
        "rooms/1/a b+c.png",
        "rooms/1/한글.png",
        "rooms/1/x~y_z-.png",
    })
    void 저장소_키_형식이_달라도_presigner와_같다(String key) {
        assertSameAsPresigner(new StorageKey(key), "inline", "image/webp", Duration.ofMinutes(10));
    }

    @Test
    void 응답_헤더_지정이_없으면_쿼리에서_빠지는_것도_presigner와_같다() {
        assertSameAsPresigner(new StorageKey("rooms/1/a.png"), null, null, Duration.ofMinutes(5));
    }

    @Test
    void 수명은_서명_시각부터_센다() {
        Instant signedAt = Instant.parse("2026-11-03T04:00:00Z");
        R2GetObjectSigner signer = new R2GetObjectSigner(PROPERTIES, Clock.fixed(signedAt, ZoneOffset.UTC));

        String url = signer.sign(new StorageKey("rooms/1/a.png"), "inline", "image/png", Duration.ofMinutes(5));

        assertThat(url).contains("X-Amz-Date=20261103T040000Z").contains("X-Amz-Expires=300");
    }

    // presigner 는 시계를 바꿀 수 없어, presigner 가 찍은 서명 시각으로 직접 서명해 비교한다.
    private void assertSameAsPresigner(StorageKey key, String disposition, String contentType, Duration ttl) {
        String expected = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(PROPERTIES.bucket())
                    .key(key.value())
                    .responseContentDisposition(disposition)
                    .responseContentType(contentType)
                    .build())
                .build())
            .url()
            .toString();
        Clock signedAt = Clock.fixed(Instant.from(AMZ_DATE.parse(amzDateOf(expected))), ZoneOffset.UTC);

        String actual = new R2GetObjectSigner(PROPERTIES, signedAt).sign(key, disposition, contentType, ttl);

        assertThat(actual).isEqualTo(expected);
    }

    private static String amzDateOf(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
            .filter(parameter -> parameter.startsWith("X-Amz-Date="))
            .map(parameter -> parameter.substring("X-Amz-Date=".length()))
            .findFirst()
            .orElseThrow();
    }
}
