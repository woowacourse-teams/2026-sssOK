package com.sssok.infrastructure.storage;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.R2Properties;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

// region "auto" 와 path-style 은 R2 의 제약이다. 실제 리전 값을 넣으면 서명 스코프가 어긋나
// 요청이 거부된다 (docs/backend/R2_PRESIGNED_UPLOAD.md).
//
// 클라이언트를 생성자가 아니라 처음 쓸 때 만든다. 생성자에서 만들면 자격증명이 없는 환경에서
// 스프링 컨텍스트 자체가 뜨지 않아, R2 를 쓰지 않는 테스트까지 전부 함께 죽는다.
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

    @Override
    public void delete(StorageKey storageKey) {
        client().deleteObject(DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey.value())
            .build());
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

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
