package com.sssok.infrastructure.storage;

import com.sssok.application.port.out.AbortableOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

// S3/R2 멀티파트 업로드로 임의 크기의 데이터를 스트리밍으로 올린다. zip처럼 전체 크기를
// 미리 알 수 없는 결과물을 힙에 통째로 들고 있지 않기 위한 용도다 — 파트 크기(PART_SIZE)만큼만
// 버퍼링했다가 그때그때 올린다. 마지막 파트는 S3 규격상 5MB 미만이어도 된다.
class S3MultipartOutputStream extends AbortableOutputStream {

    // S3/R2는 마지막 파트를 제외한 모든 파트가 5MB 이상이어야 한다. 여유를 두고 8MB로 잡는다.
    private static final int PART_SIZE = 8 * 1024 * 1024;

    private final S3Client client;
    private final String bucket;
    private final String key;
    private final String uploadId;
    private final List<CompletedPart> completedParts = new ArrayList<>();

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream(PART_SIZE);
    private int partNumber = 1;
    private boolean finished = false;

    S3MultipartOutputStream(S3Client client, String bucket, String key, String contentType) {
        this.client = client;
        this.bucket = bucket;
        this.key = key;
        this.uploadId = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build())
            .uploadId();
    }

    @Override
    public void write(int b) {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        buffer.write(b, off, len);
        if (buffer.size() >= PART_SIZE) {
            flushPart();
        }
    }

    private void flushPart() {
        byte[] data = buffer.toByteArray();
        buffer.reset();

        UploadPartResponse response = client.uploadPart(
            UploadPartRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build(),
            RequestBody.fromBytes(data));

        completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build());
        partNumber++;
    }

    // 버퍼에 남은 마지막 조각을 올리고 멀티파트 업로드를 완료한다.
    @Override
    public void close() {
        if (finished) {
            return;
        }
        finished = true;
        // 파트가 하나도 안 나갔으면(전체가 PART_SIZE 미만) 완료에 최소 1개는 있어야 하므로
        // 버퍼가 비어 있어도 그대로 마지막 파트로 올린다.
        if (buffer.size() > 0 || completedParts.isEmpty()) {
            flushPart();
        }
        client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build());
    }

    // 지금까지 올라간 파트를 스토리지에서 지운다. close() 대신 이걸 부르면 완료되지 않은
    // 업로드가 남지 않는다. close()와 abort()는 하나만 호출해야 한다.
    @Override
    public void abort() {
        if (finished) {
            return;
        }
        finished = true;
        client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .build());
    }
}
