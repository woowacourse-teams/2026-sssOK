package com.sssok.renewal.outgoing.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
class SinglePutUploadStrategy implements R2UploadStrategy {
    @Override
    public void upload(S3Client client, String bucket, String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(content));
    }
}
