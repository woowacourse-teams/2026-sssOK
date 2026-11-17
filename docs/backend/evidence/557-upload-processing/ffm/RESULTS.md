# FFM/JNI 동일 조건 비교와 최종 선택 — #557

2026-11-17. **운영 후보는 Java 21 + libvips JNI다.** 사용자 우선순위는 속도다.
FFM은 직접 작성하는 C 코드를 없애면서 JNI에 가까운 성능을 보였지만, 현재 워커 수인 동시성 4에서는
교차 실행한 네 회차 모두 JNI의 평균 배치 시간이 짧았다. 작은 로컬 차이이며 JNI가 모든 환경에서 빠르다는 뜻은 아니다.
코드는 로컬 검증 완료 단계이며 커밋·푸시·PR·배포는 별도다.

## 같은 조건으로 무엇을 비교했나

- 동일한 Alpine Linux ARM64 이미지·libvips 8.18.2·JNI 바이너리에 JDK 25를 설치하여 두 방식 모두 사용.
- 2 CPU, 컨테이너 1536MiB, swap 추가 허용 없음, Java 힙 512MiB, libvips 내부 동시성 1·연산 캐시 0.
- 기존 JPEG 원본 6장을 메모리에 미리 읽고 20장 배치를 구성. 원본 해시는 `summary.json`에 기록.
- 썸네일 400px/Q80, 프리뷰 1600px/Q85, effort 0, EXIF 자동 방향 보정, shrink-on-load,
  공유 픽셀 1회 materialize, linear 리사이즈, 메타데이터 제거. 양쪽 입력 복사·출력 byte[] 복사·자원 해제를 포함.
- 매 JVM에서 20장 배치 2회 워밍업 후 5회 측정. c1은 2회차, c4는 4회차, JNI→FFM / FFM→JNI 교차 실행.
- 본 비교 1,200장 측정, Java 21 JNI 대조군 200장 측정. 워밍업은 통계에서 제외.
- FFM은 `com.lopcode.vips-ffm:vips-ffm-core:1.11.1`. 각 작업의 confined Arena가 native 자원을 소유하고,
  결과를 Java byte[]로 복사한 다음 Arena를 닫는다. 포인터를 업로드 스레드에 넘기지 않는다.

## 결과

시간은 **20장 전체 배치 완료 시간**이며 이미지 한 장의 지연이나 서비스 READY 시간이 아니다.

| 조건 | JNI 평균 / 중앙값 | FFM 평균 / 중앙값 | 관찰 |
| --- | ---: | ---: | --- |
| 동시성 1 | 2,025.68 / 1,984.99ms | 1,938.84 / 1,937.79ms | FFM 평균 약 4.3% 짧음 |
| 동시성 4 | 1,081.26 / 1,079.39ms | 1,123.40 / 1,102.42ms | FFM 평균 약 3.9% 더 걸림 |
| 동시성 4 컨테이너 메모리 peak | 219.14–223.11MiB | 238.63–244.60MiB | JVM·네이티브·페이지 캐시 포함 |

동시성 4 회차별 평균(ms):

| 회차 | JNI | FFM |
| --- | ---: | ---: |
| 1 | 1,072.95 | 1,095.72 |
| 2 | 1,066.54 | 1,098.52 |
| 3 | 1,097.06 | 1,201.55 |
| 4 | 1,088.48 | 1,097.80 |

3회차 FFM 지연을 임의로 제거하지 않았다. 중앙값 차이는 약 2.1%로 평균보다 작다.
차이는 환경 변동의 영향을 받으므로 FFM의 본질적 성능 한계나 통계적 우월성을 주장하지 않는다.
같은 이미지의 Java 21 JNI 대조군은 20장 평균 **1,067.43ms**로, JDK 업그레이드 없이도 선택한 경로의 성능이 유지됐다.
실제 인스턴스가 x86_64이거나 CPU·동시성이 바뀌면 그 환경에서 재측정한다.

## 출력과 기능

- 6개 조건 회차 × 원본 6장 × 파생본 2종 = **JNI/FFM 출력 72쌍이 바이트까지 동일**.
- FFM/JNI/Java 공통 계약: EXIF 방향·반전, 작은 원본, 다양한 출력 너비, preview 없음,
  PNG/다른 출력 포맷의 Java fallback, 품질 전달, 실패 후 정상 처리, 과대 픽셀 헤더 거절을 검증한다.
- 네이티브 테스트의 실패·skip·abort가 있으면 CI를 실패시킨다. 컨테이너 자체 abort도 검사한다.
- 최종 검증: 후처리 관련 단위 61개·H2 통합 28개, 운영 이미지 app 사용자 계약 96개, FFM/JNI/Java 계약 116개 모두 통과(실패·skip·abort 0). 서로 중복되는 계약이 있어 합산하지 않는다.
- `validation.json`에는 테스트 수·JAR 구성·컨테이너 검증 결과를 기록한다.

## 왜 JNI를 선택했나

FFM은 충분히 유력한 대안이다. 다만 이번에는 현재 동시성에서의 속도를 우선하고,
FFM 바인딩 때문에 애플리케이션 Java 기준을 올리는 변경까지 가져갈 근거가 부족했다.
[바인딩 공식 문서](https://github.com/lopcode/vips-ffm)는 JDK 23 이상을 요구한다.
직접 JNI는 JPEG→WebP 한 경로에만 한정하며, 디코더·인코더를 직접 구현하지 않고 libvips를 호출한다.

최종 운영 코드는 `LibvipsImageProcessor` + `src/main/native/sssok_vips.c`이고,
Docker의 빌드 단계가 C 연결 계층을 컴파일한다. 최종 이미지에는 컴파일러·헤더가 들어가지 않는다.
CLI는 `src/test/java`, FFM은 `src/test/ffm`에만 보존하여 서비스 JAR에서 제외한다.
`MEDIA_IMAGE_PROCESSOR=java`로 재시작하면 기존 Java 경로로 복귀한다.
지원하지 않는 입력/출력 포맷은 기존 Java 처리기로 처리하고, JPEG 네이티브 실패를 조용히 fallback하지 않는다.

## 측정 경계와 다음 단계

다운로드·R2 PUT·DB 큐·AI·READY는 이번 비교에서 제외했다. 앞선 CLI/JNI 측정과도 JDK·회차가 달라
절대시간을 섞어 개선율을 만들지 않는다. 220MiB라는 이미지 하네스 결과를 API 서버의 필요 메모리로 사용하지 않는다.
[전체 적용 및 인스턴스 분리 계획](../../../UPLOAD_PROCESSING_OPTIMIZATION.md)을 따른다.

## 재현

`backend`에서 `./gradlew prepareNativeImageTest` 실행 후, 저장소 루트에서:

```sh
python3 docs/backend/evidence/557-upload-processing/ffm/run-comparison.py \
  --input /path/to/six-jpegs --output /tmp/sssok-ffm-comparison
```

Docker와 네트워크(Maven·컨테이너 이미지 다운로드)가 필요하다. 스크립트는 전용 이미지를 빌드하고
FFM jar 해시를 검증한 뒤 컴파일·계약 검사·동일 조건 교차 실험·Java 21 대조군을 실행한다.
개인 원본과 생성 이미지는 지정한 외부 출력 디렉터리에만 둔다. 저장소에는 원시 CSV·요약·로그만 보존했다.
