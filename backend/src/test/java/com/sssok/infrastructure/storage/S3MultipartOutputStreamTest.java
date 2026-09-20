package com.sssok.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

class S3MultipartOutputStreamTest {

    private static final int PART_SIZE = 8 * 1024 * 1024;

    private final S3Client client = mock(S3Client.class);
    private final List<byte[]> uploadedParts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        given(client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .willReturn(CreateMultipartUploadResponse.builder().uploadId("upload-id").build());
        given(client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
            .willAnswer(invocation -> {
                RequestBody body = invocation.getArgument(1);
                try (InputStream input = body.contentStreamProvider().newStream()) {
                    uploadedParts.add(input.readAllBytes());
                }
                return UploadPartResponse.builder()
                    .eTag("etag-" + uploadedParts.size())
                    .build();
            });
    }

    @Test
    void 한_번의_write가_여러_파트를_넘어도_파트_경계에_맞춰_업로드한다() {
        byte[] data = sequentialBytes(PART_SIZE * 2 + 123);
        S3MultipartOutputStream stream = stream();

        stream.write(data, 0, data.length);
        stream.close();

        assertThat(uploadedParts).extracting(part -> part.length)
            .containsExactly(PART_SIZE, PART_SIZE, 123);
        assertThat(concat(uploadedParts)).isEqualTo(data);
    }

    @Test
    void 여러_write에_걸친_데이터도_가득_찬_파트만_먼저_업로드한다() {
        byte[] data = sequentialBytes(PART_SIZE * 2 + 1);
        S3MultipartOutputStream stream = stream();

        stream.write(data, 0, PART_SIZE - 1);
        stream.write(data, PART_SIZE - 1, PART_SIZE + 2);
        stream.close();

        assertThat(uploadedParts).extracting(part -> part.length)
            .containsExactly(PART_SIZE, PART_SIZE, 1);
        assertThat(concat(uploadedParts)).isEqualTo(data);
    }

    @Test
    void 정확히_파트_경계에서_끝나면_빈_마지막_파트를_추가하지_않는다() {
        S3MultipartOutputStream stream = stream();

        byte[] data = sequentialBytes(PART_SIZE);
        stream.write(data, 0, data.length);
        stream.close();

        assertThat(uploadedParts).extracting(part -> part.length)
            .containsExactly(PART_SIZE);
    }

    @Test
    void 파트보다_작은_데이터는_마지막_파트_하나로_업로드한다() {
        byte[] data = sequentialBytes(1024);
        S3MultipartOutputStream stream = stream();

        stream.write(data, 0, data.length);
        stream.close();

        assertThat(uploadedParts).hasSize(1);
        assertThat(uploadedParts.getFirst()).isEqualTo(data);
    }

    private S3MultipartOutputStream stream() {
        return new S3MultipartOutputStream(client, "bucket", "key", "application/zip");
    }

    private byte[] sequentialBytes(int size) {
        byte[] data = new byte[size];
        for (int index = 0; index < size; index++) {
            data[index] = (byte) index;
        }
        return data;
    }

    private byte[] concat(List<byte[]> parts) {
        int totalSize = parts.stream().mapToInt(part -> part.length).sum();
        byte[] result = new byte[totalSize];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, position, part.length);
            position += part.length;
        }
        return result;
    }
}
