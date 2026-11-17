# libvips 연결 방식 비교와 선택 — 2026-11-17

> 이 문서는 이전 CLI 후보의 실험 이력이다. 최종 선택은 [FFM/JNI 비교](../ffm/RESULTS.md)의 **JNI**로 변경됐다. 아래 배치·소스 경로 설명은 당시 상태다.

## 결정

JPEG → WebP 경로에 **libvips CLI**를 채택한 로컬 후보를 구현했다.
직접 JNI보다 느리지만 Java method 0 대비 가공 속도와 컨테이너 메모리가 개선됐고,
서비스에서 C 코드·JNI 바인딩·네이티브 빌드를 관리하지 않아도 된다.
아직 커밋·푸시·PR·dev 배포하지 않았다. 실제 서비스 READY 개선은 미측정이다.

## 동일 조건 비교

기존 4032×3024 JPEG 6장을 순환해 20장씩 처리했다.
로컬 Docker Linux ARM64 Alpine, libvips 8.18.2, 2 CPU·1536MiB, Xmx512MiB.
동시성 1/4 각각 독립 JVM 2회, 엔진 실행 순서를 역전했다.
각 JVM은 20장 1회 워밍업 후 20장 3회 측정했다. **측정 720/720 성공**.
Java는 기존 method 0, JPEG 축소 디코딩·픽셀 재사용을 유지했다.
썸네일 400px Q80, 프리뷰 1600px Q85. libvips effort 0·내부 동시성 1·연산 캐시 0.

| 지표 | Java method 0 | JNI | CLI |
| --- | ---: | ---: | ---: |
| 동시성 1 / 20장 평균 | 3.227초 | 1.877초 | **2.737초** |
| 동시성 4 / 20장 평균 | 2.023초 | 1.241초 | **1.600초** |
| 동시성 4 / 컨테이너 피크 메모리 | 494–501MiB | 227–228MiB | **274–275MiB** |

CLI는 Java 대비 순차 약 15.2%, 동시성 4 약 20.9% 단축했다.
JNI 대비 동시성 4에서는 약 28.9% 오래 걸린다. CLI가 가장 빠른 선택은 아니다.
메모리는 전체 실행(워밍업·비교 이미지 출력 포함)의 cgroup `memory.peak`다.
JVM·자식 프로세스·파일 캐시가 포함되며, 이전 실험의 프로세스 RSS와 직접 비교하지 않는다.

원본은 미리 메모리에 읽었다. CLI의 임시 파일 쓰기·프로세스 3회 실행·출력 읽기·정리는 시간에 포함된다.
R2 GET/PUT, DB, AI, 큐 대기, SSE는 제외했다. 따라서 이 20.9%를 실제 READY 개선율로 쓰지 않는다.
이전 Ubuntu/libvips 8.15.1 실험과 절대시간을 섞지 않는다.

## 출력 검증

두 회차·두 동시성·6장·파생본 2개, **48쌍의 CLI/JNI 출력이 바이트 단위로 일치**했다.
즉 이번 CLI 호출·중간 파일 사용이 같은 버전 JNI 결과를 바꾸지 않았다.
이는 Java와 동일 화질이라는 뜻은 아니다. 리사이즈 엔진과 코덱이 달라 Java와 결과는 다르다.
기존 JNI 화질 비교의 한계는 유지하며, 운영 확대 전 실제 사진 육안 검토가 필요하다.

기존 계약 테스트로 EXIF 방향 1–8, 원본 치수, 작은 이미지 확대 방지,
촬영 시각·GPS, 투명 PNG 등 기존 Java fallback을 확인했다.
CLI가 직접 처리하는 포맷은 JPEG뿐이다. PNG·다른 출력 형식은 기존 Java가 담당한다.

## 최종 구현

- `ImageDerivativeEngine` 경계로 Java 처리기에서 선택한 외부 엔진을 호출한다.
- `LibvipsCliImageProcessor`: 헤더에서 원본 표시 치수를 확인하고 픽셀 상한을 검사한다.
- `vips thumbnail`: 원본을 한 번 축소 디코딩·회전해 무손실 `.v` 파일을 만든다.
- `vips resize` 2회: 공유 픽셀에서 썸네일·프리뷰를 만들고 각각 WebP로 저장한다.
- 작업별 임시 디렉터리, 전체 프로세스 예산 기본 20초, 출력 형식·크기 검사.
- 실패·시간 초과·인터럽트 시 자식 종료를 기다린 뒤 임시 파일을 정리한다.
- 이미지 워커 동시성으로 프로세스 수를 제한한다. 각 워커는 CLI를 순차 실행한다.
- 두 파생본 PUT은 기존에 구현한 제한 병렬 업로드를 사용한다.
- DB claim·lease·READY·SSE·AI 및 영상 처리 경로는 유지한다.

Docker는 `ffmpeg vips-tools`만 설치하며 C 컴파일러·직접 JNI 라이브러리는 포함하지 않는다.
컨테이너 기본값은 `MEDIA_IMAGE_PROCESSOR=libvips-cli`다.
`MEDIA_IMAGE_PROCESSOR=java`로 재시작하면 기존 Java method 0 경로로 복귀한다.
CLI 변환 실패를 조용히 Java로 재시도하지 않고 기존 작업 재시도 경로로 전달한다.
JNI Java/C 소스는 `backend/src/test`에 보존했다. 비교 전용 Dockerfile도 함께 있다.

## 최종 검증

- 선택한 일반 단위 테스트 **62/62 통과**, 스킵 없음.
- 후처리 H2 통합 테스트 **28/28 통과**, 스킵 없음.
- 최종 배포 이미지의 일반 사용자 `app` 권한에서 Java/CLI 이미지 계약과 실제 FFmpeg 영상 테스트 **95/95 통과**, 스킵 없음.
- CLI 전체 시간 초과·인터럽트 보존·프로세스 종료·임시 파일 정리·출력 누락 실패 확인.
- Spring CLI 선택/Java 복귀 설정과 기본 시간 제한 바인딩 확인.
- 서비스 JAR에 직접 JNI 클래스가 없고 CLI 클래스만 포함됨을 확인.
- `git diff --check` 통과.

단위 테스트와 컨테이너 계약 테스트의 일부 사례는 중복되므로 합산해 고유 테스트 수로 표현하지 않는다.

## 관측과 남은 검증

기존 `derive` 전체 트레이스와 GET/PUT·READY 계측은 유지한다.
CLI 내부 디코딩·리사이즈·인코딩은 기존 Java 메서드를 거치지 않으므로 해당 단계 타이머가 찍히지 않는다.
이 타이머가 비어 있는 것을 연산 비용 0으로 해석하면 안 된다. 이번 비교는 가공 전체 시간을 기준으로 한다.

- 실제 dev 20장: 같은 원본·API/AI·메모리 조건으로 큐/READY·실패·재시도 비교.
- 장시간·혼합 부하, 비정상 대형 JPEG·ICC/CMYK 등 지원 범위 추가 검증.
- CLI의 디스크 I/O 비용은 호스트에 따라 달라지므로 EC2에서도 이점 확인.
- 임시 파일은 정상 완료/실패 시 제거된다. JVM SIGKILL/호스트 장애는 finally를 실행하지 못하므로
  컨테이너 교체로 임시 레이어를 폐기하거나 운영 청소 정책이 필요하다.
- 인스턴스 분리는 이번 변경에 포함하지 않았다.

## 재현

저장소 루트에서:

```sh
cd backend
./gradlew prepareNativeImageTest
docker build --target native-test -t sssok-cli-contract:557 .
docker build -f src/test/native/Dockerfile -t sssok-image-engine-bench:557 .
cd ..
python3 docs/backend/evidence/557-upload-processing/cli/run-comparison.py \
  --input /path/to/six-original-jpegs --output /path/to/results
```

배치 시간·건별 시간·출력 바이트·메모리·CPU 원시 기록은 이 폴더에 보존했다.
입력 해시는 `summary.json`에 있다. 원본 사진·인증정보는 저장소에 추가하지 않았다.
