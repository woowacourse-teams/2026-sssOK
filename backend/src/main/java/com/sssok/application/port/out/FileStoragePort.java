package com.sssok.application.port.out;

import com.sssok.domain.file.StorageKey;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

// 오브젝트 스토리지 출력
public interface FileStoragePort {

    // 클라이언트가 직접 PUT 할 수 있는 서명 URL. contentType 은 서명 대상에 포함되므로
    // 업로드하는 쪽이 같은 값을 헤더에 실어야 한다 (docs/backend/R2_PRESIGNED_UPLOAD.md).
    String presignPut(StorageKey storageKey, String contentType, Duration ttl);

    // 클라이언트가 직접 GET 할 수 있는 서명 URL. responseContentDisposition/responseContentType 은
    // 서명에 포함되어, 브라우저가 이 값 그대로 다운로드 헤더를 받는다(스토리지가 원본 메타데이터
    // 대신 이 값으로 응답하도록 강제).
    String presignGet(StorageKey storageKey, String responseContentDisposition,
                      String responseContentType, Duration ttl);

    // 실제로 올라왔는지와 올라온 것이 무엇인지 확인한다. 없으면 비어 있다.
    Optional<UploadedObject> findUploaded(StorageKey storageKey);

    // 원본 파일 하나를 읽어오는 스트림. zip 압축 워커가 파일 전체를 힙에 올리지 않고
    // 스트리밍으로 복사할 수 있도록 한다. 호출한 쪽이 close()로 반드시 닫아야 한다.
    InputStream openDownloadStream(StorageKey storageKey);

    // 없는 키를 지워도 성공으로 본다 — 배치가 다시 돌아도 안전해야 한다.
    void delete(StorageKey storageKey);

    record UploadedObject(long sizeBytes, String contentType) {
    }
}
