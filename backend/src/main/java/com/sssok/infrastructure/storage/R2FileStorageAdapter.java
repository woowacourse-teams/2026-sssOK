package com.sssok.infrastructure.storage;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.R2Properties;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

// region "auto" 와 path-style 은 R2 의 제약이다. 실제 리전 값을 넣으면 서명 스코프가 어긋나
// 요청이 거부된다 (docs/backend/R2_PRESIGNED_UPLOAD.md).
//
// 클라이언트를 생성자가 아니라 처음 쓸 때 만든다. 생성자에서 만들면 자격증명이 없는 환경에서
// 스프링 컨텍스트 자체가 뜨지 않아, R2 를 쓰지 않는 테스트까지 전부 함께 죽는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class R2FileStorageAdapter implements FileStoragePort {

    private final R2Properties properties;

    private volatile S3Presigner presigner;
    private volatile S3Client client;

    // contentType 을 지정하면 서명 대상에 포함된다. 업로드하는 쪽이 같은 값을 보내지 않으면 403 이다.
    @Override
    public String presignPut(StorageKey storageKey, String contentType, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey.value())
            .contentType(contentType)
            .build();

        return presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build())
            .url()
            .toString();
    }

    // responseContentDisposition/responseContentType 을 지정하면 스토리지가 원본 메타데이터
    // 대신 이 값으로 응답 헤더를 채운다 — 업로드 당시 파일명을 그대로 내려주는 데 쓴다.
    @Override
    public String presignGet(StorageKey storageKey, String responseContentDisposition,
                             String responseContentType, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey.value())
            .responseContentDisposition(responseContentDisposition)
            .responseContentType(responseContentType)
            .build();

        return presigner().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build())
            .url()
            .toString();
    }

    @Override
    public Optional<UploadedObject> findUploaded(StorageKey storageKey) {
        try {
            HeadObjectResponse head = client().headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey.value())
                .build());
            return Optional.of(new UploadedObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    // 호출한 쪽이 반드시 닫아야 커넥션이 반환된다. zip 압축 워커가 try-with-resources로 감싸 쓴다.
    @Override
    public InputStream openDownloadStream(StorageKey storageKey) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey.value())
            .build();
        return client().getObject(get, ResponseTransformer.toInputStream());
    }

    @Override
    public AbortableOutputStream openUploadStream(StorageKey storageKey, String contentType) {
        return new S3MultipartOutputStream(client(), properties.bucket(), storageKey.value(), contentType);
    }

    @Override
    public void delete(StorageKey storageKey) {
        client().deleteObject(DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey.value())
            .build());
    }

    // 키 1000 개마다 요청 한 번이다. 없는 키가 섞여 있어도 DeleteObjects 는 성공으로 처리하므로
    // 단건 delete 와 같은 계약이 유지된다.
    @Override
    public List<StorageKey> deleteAll(List<StorageKey> storageKeys) {
        if (storageKeys.isEmpty()) {
            return List.of();
        }
        List<StorageKey> failed = new ArrayList<>();
        for (List<StorageKey> chunk : StorageKeyBatches.chunk(storageKeys)) {
            failed.addAll(deleteChunk(chunk));
        }
        return List.copyOf(failed);
    }

    // 한 청크가 통째로 실패해도 나머지 청크는 계속 시도한다. 여기서 예외를 그대로 띄우면
    // 1000 개 중 앞쪽 하나가 터졌다는 이유로 뒤쪽 수천 개가 영영 남는다.
    private List<StorageKey> deleteChunk(List<StorageKey> chunk) {
        try {
            DeleteObjectsResponse response = client().deleteObjects(DeleteObjectsRequest.builder()
                .bucket(properties.bucket())
                .delete(Delete.builder()
                    // 성공 목록은 쓰지 않는다. 1000 건을 돌려받아 버리기만 할 이유가 없다.
                    .quiet(true)
                    .objects(chunk.stream()
                        .map(key -> ObjectIdentifier.builder().key(key.value()).build())
                        .toList())
                    .build())
                .build());
            return failedKeysOf(response);
        } catch (SdkException e) {
            log.warn("오브젝트 배치 삭제 요청이 실패했습니다. 회수 배치가 다시 시도합니다. keys={}",
                chunk.size(), e);
            return chunk;
        }
    }

    // 부분 실패는 예외가 아니라 응답의 errors 로 온다. 이것을 보지 않으면 지워지지 않은 키를
    // 지운 것으로 착각해 회수 대상에서 놓친다.
    private List<StorageKey> failedKeysOf(DeleteObjectsResponse response) {
        if (!response.hasErrors() || response.errors().isEmpty()) {
            return List.of();
        }
        for (S3Error error : response.errors()) {
            log.warn("오브젝트를 지우지 못했습니다. key={}, code={}, message={}",
                error.key(), error.code(), error.message());
        }
        return response.errors().stream()
            .map(error -> new StorageKey(error.key()))
            .toList();
    }

    private S3Presigner presigner() {
        S3Presigner local = presigner;
        if (local == null) {
            synchronized (this) {
                local = presigner;
                if (local == null) {
                    local = S3Presigner.builder()
                        .endpointOverride(URI.create(properties.endpoint()))
                        .region(Region.of("auto"))
                        .credentialsProvider(credentials())
                        .serviceConfiguration(pathStyle())
                        .build();
                    presigner = local;
                }
            }
        }
        return local;
    }

    private S3Client client() {
        S3Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = S3Client.builder()
                        .endpointOverride(URI.create(properties.endpoint()))
                        .region(Region.of("auto"))
                        .credentialsProvider(credentials())
                        .serviceConfiguration(pathStyle())
                        .build();
                    client = local;
                }
            }
        }
        return local;
    }

    // S3Client 는 안에 커넥션 풀과 스레드를 들고 있어, 닫지 않으면 종료가 늦어진다.
    @PreDestroy
    void closeClients() {
        if (presigner != null) {
            presigner.close();
        }
        if (client != null) {
            client.close();
        }
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
