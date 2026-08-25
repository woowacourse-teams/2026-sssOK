package com.sssok.infrastructure.storage;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StorageKey;
import org.springframework.stereotype.Component;

// FileStoragePort의 Cloudflare R2 구현체.
// TODO(#25): 업로드가 붙어 실물 파일이 생기면 S3 클라이언트로 실제 삭제를 채운다.
@Component
public class R2FileStorageAdapter implements FileStoragePort {

    @Override
    public void delete(StorageKey storageKey) {
    }
}
