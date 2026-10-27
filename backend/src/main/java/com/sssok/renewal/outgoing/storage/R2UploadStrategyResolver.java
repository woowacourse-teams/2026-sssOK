package com.sssok.renewal.outgoing.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R2UploadStrategyResolver {
    static final int MULTIPART_THRESHOLD = 8 * 1024 * 1024;

    private final SinglePutUploadStrategy singlePut;
    private final MultipartUploadStrategy multipart;

    R2UploadStrategy resolve(int contentLength) {
        return contentLength < MULTIPART_THRESHOLD ? singlePut : multipart;
    }
}
