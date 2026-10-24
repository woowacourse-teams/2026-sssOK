package com.sssok.renewal.outgoing.storage;

import software.amazon.awssdk.services.s3.S3Client;

interface R2UploadStrategy {
    boolean supports(int contentLength);

    void upload(S3Client client, String bucket, String key, byte[] content, String contentType);
}
