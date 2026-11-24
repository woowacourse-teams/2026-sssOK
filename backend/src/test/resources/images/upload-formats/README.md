# 업로드 형식 테스트 파일

외부 사진이 아닌 직접 만든 96×64 RGBA 패턴이다. 왼쪽은 빨강, 오른쪽 위는 파랑,
오른쪽 아래는 투명하다. libvips 8.18.2와 libheif 1.23.7로 인코딩했다.

- `transparent.webp`: libvips WebP 무손실 인코딩. 투명도 검증.
- `landscape.heic`: libvips HEVC 인코딩(Q=90). 색상·이미지 크기 검증.
- `rotated.heif`: landscape를 libheif로 읽고 `heif_encoding_options.image_orientation`을
  `heif_orientation_rotate_90_cw`로 설정해 HEVC 재인코딩. 컨테이너 회전 적용 후 64×96이어야 한다.
  EXIF orientation만 붙인 HEIF는 실제 회전 속성과 다르므로 이 테스트를 대체할 수 없다.

같은 파일을 일반 경로와 구간별 진단 경로에서 모두 테스트한다. 잘린 입력의 실패 후
정상 입력이 처리되는지도 확인한다.
