# R2 Presigned 업로드 제약사항

이슈 #16 스파이크에서 Cloudflare R2에 presigned PUT으로 이미지를 실제 업로드해보고 확인한 내용이다.
업로드 API(`IssueUploadUrlsService`, `R2FileStorageAdapter`)를 구현할 때 여기 적힌 제약을 그대로 가져가면 된다.

검증 코드는 `backend/src/test/java/com/sssok/spike/R2PresignedUploadSpikeTest.java` 에 있으며,
**업로드 API를 구현하면 삭제한다.**

스파이크 과정에서 임시 컨트롤러(`POST /spike/upload-urls`)를 만들어
클라이언트 → 서버(URL 발급) → 클라이언트 → R2(직접 업로드) 흐름 전체를 curl로도 확인했다.
인증이 없는 엔드포인트라 확인 후 삭제했고, 아래 헤더 규칙은 그때 얻은 결과다.

---

## 검증한 것

| 항목 | 결과 |
|---|---|
| presigned PUT URL 발급 | 성공 |
| 발급 URL로 PUT 업로드 | **200**, PNG 3151 bytes |
| 업로드된 파일 확인 | presigned GET으로 되받아 **바이트 완전 일치** |
| 파일 무결성 | `ImageIO.read()` 디코딩 성공, 320x180 유지 |
| Content-Type 보존 | GET 응답에 `image/png` 그대로 반환 |
| Content-Type 불일치 | **403** (서명 거부) |

---

## 1. 서명 방식

AWS SDK v2의 `S3Presigner`를 그대로 쓴다. 별도 의존성은 필요 없고 `software.amazon.awssdk:s3` 안에 들어 있다.

```java
S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of("auto"))
        .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
        .build();
```

**region은 `auto` 고정.** R2에는 리전 개념이 없다. `ap-northeast-2` 같은 실제 리전 값을 넣으면 서명 스코프가 어긋나 요청이 거부된다.

**path-style을 켜야 한다.** SDK 기본값인 virtual-hosted 방식(`<bucket>.<account>.r2.cloudflarestorage.com`) 대신
`pathStyleAccessEnabled(true)`를 지정한다. 그러면 URL이 `<endpoint>/<bucket>/<key>` 형태로 생성된다.

**endpoint에 버킷명을 붙이지 않는다.** `https://<account-id>.r2.cloudflarestorage.com` 까지만이다.
버킷은 `PutObjectRequest.bucket()` 으로 따로 넘긴다.

---

## 2. 헤더 규칙 — 가장 중요한 제약

발급 시점에 `contentType()`을 지정하면 **Content-Type이 서명 대상에 포함된다.** 생성된 URL에 이렇게 드러난다.

```
X-Amz-SignedHeaders=content-type%3Bhost
```

즉 **업로드하는 쪽이 보내는 `Content-Type` 헤더가 발급 시점의 값과 정확히 같아야 한다.**
`image/png`로 서명한 URL에 세 가지 경우로 PUT을 보내 직접 확인했다.

| PUT 요청의 Content-Type | 결과 |
|---|---|
| `image/png` (서명값과 동일) | **200** |
| `application/octet-stream` (다른 값) | **403** |
| 헤더 자체를 생략 | **403** |

**헤더를 빼는 것도 실패한다.** 서명에 포함된 헤더는 반드시 존재하면서 값도 같아야 한다.
클라이언트가 "잘 모르겠으면 안 보내면 되겠지"로 처리하면 업로드가 전부 깨진다.

### API 설계에 주는 영향

업로드 URL 발급 응답에 **Content-Type을 함께 내려줘야 한다.** 프론트가 파일 확장자로 추측하게 두면
서버가 서명한 값과 어긋나 업로드가 깨진다.

```
POST /rooms/{code}/files/upload-urls
  요청: [{ fileName, contentType, size }, ...]
  응답: [{ uploadUrl, storageKey, contentType }, ...]
                                  ^^^^^^^^^^^ 프론트는 이 값을 그대로 PUT 헤더에 사용
```

발급 요청에서 받은 `contentType`을 서버가 검증(허용 목록 대조)한 뒤, **검증에 쓴 그 값으로 서명하고
그대로 응답에 돌려주는** 흐름이 안전하다. 서버가 임의로 바꾸면 프론트와 어긋난다.

---

## 3. 만료 시간

`signatureDuration`이 URL 쿼리의 `X-Amz-Expires`에 초 단위로 박힌다. 발급 후에는 바꿀 수 없다.

`application.yml`의 `upload.presigned-url-ttl: 10m` 값을 그대로 넘기면 된다.

---

## 4. R2가 보존해주는 것

업로드 시 지정한 Content-Type을 R2가 오브젝트 메타데이터로 저장하고, 이후 GET 응답에 그대로 실어준다.
브라우저에서 이미지를 바로 렌더링하려면 이 값이 정확해야 하므로, 발급 시점의 Content-Type 검증이
다운로드 동작까지 좌우한다.

바이너리는 변형되지 않는다. 올린 3151 bytes와 내려받은 3151 bytes가 완전히 일치했다.

---

## 5. SDK 버전

`build.gradle`의 `software.amazon.awssdk:s3:2.25.0` **을 임의로 올리지 않는다.**

SDK 2.30 이상에서 flexible checksum이 기본 활성화되면서 S3 호환 스토리지의 presigned PUT이
깨진다고 알려져 있다. 다만 **이 스파이크에서 직접 확인하지는 않았다.** 버전을 올릴 일이 생기면
`R2PresignedUploadSpikeTest`를 먼저 돌려서 여전히 통과하는지 확인한 뒤 올린다.

---

## 확인하지 않은 것

업로드 API를 구현하기 전에 별도로 확인이 필요한 항목이다.

- **브라우저 CORS** — 스파이크는 Java `HttpClient`로 서버에서 PUT을 보냈다. 실제 서비스는 브라우저가
  R2로 직접 업로드하므로 **R2 버킷에 CORS 설정이 필요하다.** 이걸 안 하면 프론트 연동 시점에 막힌다.
  가장 먼저 확인할 항목.
- **대용량 파일 / multipart** — `upload.video-max-size: 1GB` 인데 스파이크는 3KB짜리 이미지만 올렸다.
  단일 PUT으로 1GB를 보내는 게 현실적인지, multipart presigned가 필요한지 미확인.
- **업로드 완료 검증** — `CompleteUploadService`에서 실제 업로드 여부와 크기를 확인하려면
  HeadObject가 필요하다. 이 경우 presigner가 아닌 `S3Client`가 있어야 하고, 동기 HTTP 클라이언트
  의존성이 추가로 필요할 수 있다.
- **키 충돌** — 스파이크는 `Instant.now().toEpochMilli()`로 키를 만들었다. 실제로는 `StorageKey`에
  충돌하지 않는 규칙(UUID 등)이 필요하다.

---

## 재현 방법

`backend/.env`에 R2 값 3개를 채운다. (`.env`는 gitignore 대상이며, 값은 Cloudflare 대시보드 >
R2 > Manage API tokens 에서 **Object Read & Write** 권한으로 발급한다.)

```
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_BUCKET=sssok-dev
```

```bash
cd backend
./gradlew cleanTest test --tests '*R2PresignedUploadSpikeTest*'
```

테스트는 `.env`를 직접 읽으므로 셸에 export 하지 않아도 된다. 자격증명이 없으면 조용히 skip된다.

**`cleanTest`를 빼면 안 된다.** 환경변수와 `.env`는 Gradle의 입력으로 추적되지 않아,
`:test UP-TO-DATE`로 건너뛰면서 실행되지 않았는데도 BUILD SUCCESSFUL이 뜬다.
결과는 `build/test-results/test/*.xml`의 `skipped="0"`으로 확인한다.