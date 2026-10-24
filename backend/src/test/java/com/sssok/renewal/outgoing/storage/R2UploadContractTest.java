package com.sssok.renewal.outgoing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.springframework.test.util.ReflectionTestUtils;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.outgoing.config.R2Properties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class R2UploadContractTest {
    @Test
    void 실제_SDK로_네트워크_없이_경로와_TTL과_ContentType을_서명한다() {
        R2FileStorage client = new R2FileStorage(new R2Properties(
            "https://storage.example.com", "test-access", "test-secret", "uploads", null), new R2UploadStrategyResolver(
            java.util.List.of(new SinglePutUploadStrategy(), new MultipartUploadStrategy())));
        try {
            URI url = URI.create(client.presignPut(new StorageKey("rooms/1/file.jpg"),
                "image/jpeg", Duration.ofSeconds(600)));
            assertThat(url.getHost()).isEqualTo("storage.example.com");
            assertThat(url.getPath()).isEqualTo("/uploads/rooms/1/file.jpg");
            String query = URLDecoder.decode(url.getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query).contains("X-Amz-Expires=600", "X-Amz-SignedHeaders=content-type;host",
                "X-Amz-Signature=", "/auto/s3/aws4_request");
        } finally {
            client.closeClients();
        }
    }

    @Test
    void HEAD의_크기와_MIME을_그대로_반환하고_클라이언트를_닫는다() {
        S3Client sdk = mock(S3Client.class);
        R2FileStorage storage = storageWith(sdk);
        when(sdk.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder().contentLength(1024L).contentType("image/jpeg").build());
        try {
            assertThat(storage.findUploaded(new StorageKey("rooms/1/file.jpg")))
                .contains(new FileStorage.UploadedObject(1024L, "image/jpeg"));
            verify(sdk).headObject(HeadObjectRequest.builder().bucket("uploads").key("rooms/1/file.jpg").build());
        } finally {
            storage.closeClients();
        }
        verify(sdk).close();
    }

    @Test
    void 없는_객체만_빈_결과로_처리한다() {
        S3Client sdk = mock(S3Client.class);
        R2FileStorage storage = storageWith(sdk);
        when(sdk.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        try {
            assertThat(storage.findUploaded(new StorageKey("rooms/1/file.jpg"))).isEmpty();
        } finally {
            storage.closeClients();
        }
    }

    @Test
    void 스토리지_장애는_파일별_미완료로_바꾸지_않고_전파한다() {
        S3Client sdk = mock(S3Client.class);
        R2FileStorage storage = storageWith(sdk);
        S3Exception.Builder failureBuilder = S3Exception.builder();
        failureBuilder.statusCode(503);
        RuntimeException failure = failureBuilder.build();
        when(sdk.headObject(any(HeadObjectRequest.class))).thenThrow(failure);
        try {
            assertThatThrownBy(() -> storage.findUploaded(new StorageKey("rooms/1/file.jpg"))).isSameAs(failure);
        } finally {
            storage.closeClients();
        }
    }

    private R2FileStorage storageWith(S3Client sdk) {
        R2FileStorage storage = new R2FileStorage(new R2Properties(
            "https://storage.example.com", "test-access", "test-secret", "uploads", null), new R2UploadStrategyResolver(
            java.util.List.of(new SinglePutUploadStrategy(), new MultipartUploadStrategy())));
        ReflectionTestUtils.setField(storage, "client", sdk);
        return storage;
    }
}
