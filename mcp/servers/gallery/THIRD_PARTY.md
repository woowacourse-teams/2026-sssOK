# 모델과 외부 코드 출처

모델 가중치와 사진은 Git·컨테이너 이미지에 포함하지 않는다. `model-checksums.json`의 SHA-256으로 기존 실험 가중치를 검증한 뒤 별도 읽기 전용 볼륨으로 공급한다.

| 구성 | 출처 | 고지 |
|---|---|---|
| YuNet | https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet | `licenses/YUNET-LICENSE` |
| SFace | https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface | `licenses/SFACE-LICENSE` |
| SSDLite320 MobileNetV3 | https://docs.pytorch.org/vision/stable/models/generated/torchvision.models.detection.ssdlite320_mobilenet_v3_large.html | torchvision 공개 모델, 기존 실험 가중치 해시 고정 |
| OSNet 코드 | https://github.com/KaiyangZhou/deep-person-reid/tree/f8cd150fdf77e8d9e1ed143b7f308c2c609ded50 | `pipeline/vendor/LICENSE`, 소스 저작권 고지 유지 |
| OSNet-x1.0 MSMT17 가중치 | https://huggingface.co/kaiyangzhou/osnet | 기존 실험에서 선택한 체크포인트 해시 고정 |

OSNet 구현에서 자동 다운로드 코드를 제거하고 모델 생성 기본값을 `pretrained=False`로 변경했다.
`pretrained=True` 요청은 명시적인 오류로 거부하며, 실행 경로에서는 검증된 로컬 체크포인트만 로드한다.
따라서 `gdown`은 서비스 의존성에 포함하지 않는다. 모델 파일을 재배포할 때는 각 배포처의 조건을 확인한다.
