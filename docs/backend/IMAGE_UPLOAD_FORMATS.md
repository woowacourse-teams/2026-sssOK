# 이미지 업로드 형식과 후처리

이슈: #881

WebP(`image/webp`), HEIC(`image/heic`), HEIF(`image/heif`)를 이미지로 받는다.
기존 JPEG·PNG·GIF와 영상 형식은 유지한다. 이미지 용량 상한도 기존 정책을 따른다.
DB의 `ck_stored_file_type` 제약은 V58 마이그레이션으로 확장한다.

## 처리 경로

- JPEG: 기존 libvips JPEG 로더와 shrink-on-load, 공유 픽셀 처리, WebP 인코딩을 유지한다.
- WebP·HEIC·HEIF: 파일 시그니처를 확인하고 libvips WebP/HEIF 로더로 읽어 WebP 썸네일과 프리뷰를 만든다.
- PNG·GIF: 기존 Java ImageIO 처리 경로를 유지한다.
- HEIF의 회전은 libheif 로더가 적용한다. EXIF 방향을 별도로 다시 적용하지 않는다.
- WebP·HEIF 파생본은 기본 이미지 한 장을 사용한다. 애니메이션·다중 이미지 보존은 별도 기능이다.
- 원본은 보존하고 화면 표시와 검색 분석에는 기존 프리뷰 경로를 사용한다.

운영 Docker 이미지의 `MEDIA_IMAGE_PROCESSOR=libvips` 설정과 `vips-heif` 패키지가 필요하다.
libvips가 꺼진 로컬 실행에서는 HEIC·HEIF 처리가 지원되지 않는다.
입력 바이트·픽셀 수·출력 크기의 기존 네이티브 제한은 새 형식에도 적용한다.

## 조회와 사진 정리

검색 문서 등록·복구·작업 선점·벡터 검색, 방 대표 사진 선택에 세 형식을 포함한다.
방·폴더 개수는 원래 형식 제한 없이 표시 상태로 집계하므로 해당 정책을 유지한다.
사진 정리는 처리 완료된 이미지 중 GIF를 제외하는 기준으로 새 형식을 포함한다.
정리 서버에는 이미 WebP·HEIC·HEIF 지원이 있고 기본 분석 입력은 WebP 프리뷰다.

## 검증

`LibvipsImageProcessorTest`와 이를 상속한 진단 경로 테스트가 실제 WebP·HEIC·회전 HEIF를
읽어 크기·색상·투명도·실패 후 복구를 확인한다. 기존 JPEG 방향·출력 일치 테스트도 함께 실행한다.
PostgreSQL 테스트는 실제 Flyway 마이그레이션과 검색 등록부터 결과 조회까지 검증한다.

```bash
cd backend
./gradlew prepareNativeImageTest
docker build --target native-test .
```

관련 서비스 테스트는 `IssueUploadUrlsServiceTest`, `RoomMembershipServiceTest`,
`MediaSearchDocumentRepositoryTest`, `OrganizeStartServiceTest`다.
PostgreSQL 테스트에는 Docker와 CI에서 사용하는 테스트용 `JWT_SECRET` 설정이 필요하다.
합성 이미지 계약 검증이며 실제 아이폰 사진 전체, HDR 색 재현, 대량 업로드 성능의 검증을 대체하지 않는다.
