package com.sssok.renewal.outgoing.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
class MultipartUploadStrategy implements R2UploadStrategy {
    @Override
    public boolean supports(int contentLength) {
        return contentLength >= R2UploadStrategyResolver.MULTIPART_THRESHOLD;
    }

    @Override
    public void upload(S3Client client, String bucket, String key, byte[] content, String contentType) {
        S3MultipartOutputStream stream = new S3MultipartOutputStream(client, bucket, key, contentType);
        try {
            stream.write(content, 0, content.length);
            stream.close();
        } catch (RuntimeException | Error failure) {
            try {
                stream.abort();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
