package com.sssok.renewal.outgoing.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class R2UploadStrategyTest {
    private final R2UploadStrategyResolver strategies = new R2UploadStrategyResolver(
        new SinglePutUploadStrategy(), new MultipartUploadStrategy());
    private final S3Client client = mock(S3Client.class);

    @ParameterizedTest
    @ValueSource(ints = {3, 8388607})
    void 경계_미만의_파생본은_내용과_메타데이터를_PUT으로_보낸다(int size) throws IOException {
        byte[] content = new byte[size];
        content[0] = 1;
        content[size - 1] = 3;
        strategies.resolve(content.length).upload(client, "bucket", "preview.webp", content, "image/webp");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
        assertThat(request.getValue().key()).isEqualTo("preview.webp");
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(content);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {8388608, 8388609})
    void 경계_이상의_결과는_멀티파트로_내용을_보존한다(int size) throws IOException {
        byte[] content = new byte[size];
        content[0] = 1;
        content[size - 1] = 2;
        when(client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload").build());
        when(client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
            .thenReturn(UploadPartResponse.builder().eTag("etag").build());
        strategies.resolve(size).upload(client, "bucket", "preview.webp", content, "image/webp");
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client, times(size == 8388608 ? 1 : 2)).uploadPart(any(UploadPartRequest.class), body.capture());
        var received = new java.io.ByteArrayOutputStream();
        for (RequestBody part : body.getAllValues()) {
            received.writeBytes(part.contentStreamProvider().newStream().readAllBytes());
        }
        assertThat(received.toByteArray()).isEqualTo(content);
        verify(client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void PUT_실패를_호출자에게_전달한다() {
        var failure = new IllegalStateException("PUT failed");
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(failure);
        assertThatThrownBy(() -> strategies.resolve(1)
            .upload(client, "bucket", "key", new byte[1], "image/webp")).isSameAs(failure);
    }

    @Test
    void 파트_실패시_중단하고_중단_실패가_원래_오류를_가리지_않는다() {
        var failure = new IllegalStateException("part failed");
        var abortFailure = new IllegalStateException("abort failed");
        when(client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload").build());
        when(client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenThrow(failure);
        when(client.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenThrow(abortFailure);
        assertThatThrownBy(() -> strategies.resolve(8388608)
            .upload(client, "bucket", "key", new byte[8388608], "image/webp"))
            .isSameAs(failure).hasSuppressedException(abortFailure);
        verify(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }
}
