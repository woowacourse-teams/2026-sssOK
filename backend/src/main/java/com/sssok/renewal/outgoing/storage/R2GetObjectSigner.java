package com.sssok.renewal.outgoing.storage;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.outgoing.config.R2Properties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

// S3Presigner 는 서명 한 번마다 실행 컨텍스트·인터셉터 체인·결과 객체를 새로 만든다. 서명 계산보다
// 이 준비 비용이 훨씬 커서, 다건 다운로드처럼 한 요청에 수백 개를 서명하면 그대로 쌓인다
// 그래서 GET 서명은 SigV4 서명기를 직접 부른다.
//
// 결과는 S3Presigner 가 R2 설정으로 만드는 URL 과 글자 단위로 같아야 한다.
// R2GetObjectSignerTest 가 이를 고정하므로, SDK 를 올려 presigner 가 달라지면 그 테스트가 먼저 깨진다.
final class R2GetObjectSigner {

    private static final AwsV4HttpSigner SIGNER = AwsV4HttpSigner.create();

    private final URI endpoint;
    private final String bucket;
    private final AwsCredentialsIdentity identity;
    private final Clock clock;

    R2GetObjectSigner(R2Properties properties) {
        this(properties, Clock.systemUTC());
    }

    R2GetObjectSigner(R2Properties properties, Clock clock) {
        this.endpoint = URI.create(properties.endpoint());
        this.bucket = properties.bucket();
        this.identity = AwsCredentialsIdentity.create(properties.accessKey(), properties.secretKey());
        this.clock = clock;
    }

    String sign(StorageKey storageKey, String responseContentDisposition, String responseContentType,
                Duration ttl) {
        SdkHttpRequest.Builder request = SdkHttpRequest.builder()
            .method(SdkHttpMethod.GET)
            .protocol(endpoint.getScheme())
            .host(endpoint.getHost())
            .port(endpoint.getPort())
            .encodedPath("/" + bucket + "/" + SdkHttpUtils.urlEncodeIgnoreSlashes(storageKey.value()));
        if (responseContentDisposition != null) {
            request.putRawQueryParameter("response-content-disposition", responseContentDisposition);
        }
        if (responseContentType != null) {
            request.putRawQueryParameter("response-content-type", responseContentType);
        }

        // 서명 URL 은 본문을 서명하지 않고, S3 는 경로를 정규화·이중 인코딩하지 않는다.
        return SIGNER.sign(signing -> signing
                .identity(identity)
                .request(request.build())
                .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, "s3")
                .putProperty(AwsV4HttpSigner.REGION_NAME, "auto")
                .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
                .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, ttl)
                .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                .putProperty(AwsV4FamilyHttpSigner.DOUBLE_URL_ENCODE, false)
                .putProperty(AwsV4FamilyHttpSigner.NORMALIZE_PATH, false)
                .putProperty(AwsV4FamilyHttpSigner.SIGNING_CLOCK, clock))
            .request()
            .getUri()
            .toString();
    }
}
