package com.sssok.application.port.out;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.domain.file.GeoPoint;
import java.time.Instant;
import java.util.Optional;

// 사진에서 파생본을 뽑는 출력 포트. 어떤 라이브러리로 줄이고 인코딩하는지는 응용 계층이 알 필요가 없다.
public interface ImageProcessorPort {

    // 썸네일과 프리뷰를 한 번에 돌려준다. 나눠 부르면 원본을 두 번 디코딩하는데, 측정해 보니
    // 디코딩+축소가 파생본 한 장을 만드는 시간의 대부분(중앙값 약 397ms)이고 인코딩은 7~100ms 에
    // 그쳤다 (docs/backend/IMAGE_DERIVATIVE_FORMAT.md). 두 번 디코딩하면 워커 점유 시간이 곱절이 된다.
    //
    // 손상됐거나 읽을 수 없는 형식이면 비어 있다 — 예외 대신 빈 값으로 두어, 사진 한 장 때문에
    // 배치가 멈추지 않게 한다.
    //
    // preview 스펙은 없을 수 있다(GIF). 그때는 결과의 preview 도 비어 있다.
    Optional<DerivedImages> derive(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview);

    // 원본과 같은 형식으로 줄이던 기존 경로. 워커를 derive 로 옮기는 후속 PR 에서 지운다.
    @Deprecated
    Optional<ProcessedImage> shrink(byte[] source, int maxWidth, String format);

    // 촬영 시각과 좌표. 카메라가 남기지 않았거나 편집 과정에서 지워졌으면 각각 비어 있다.
    // 파생본 생성과 나눈 이유는, EXIF 를 못 읽는다고 썸네일까지 포기할 이유가 없어서다.
    CaptureInfo readCaptureInfo(byte[] source);

    // 파생본 하나를 어떻게 만들지. 값은 application.yml 의 media.image 가 갖는다.
    record DerivativeSpec(int maxWidth, DerivativeFormat format, float quality) {
    }

    @Deprecated
    record ProcessedImage(int sourceWidth, int sourceHeight, byte[] content) {
    }

    record DerivedImage(byte[] content, DerivativeFormat format) {
    }

    // 원본 크기는 클라이언트가 자리를 미리 잡는 데 쓰므로 파생본 크기가 아니라 원본의 것을 담는다.
    record DerivedImages(int sourceWidth, int sourceHeight,
                         DerivedImage thumbnail, DerivedImage preview) {

        public boolean hasPreview() {
            return preview != null;
        }
    }

    record CaptureInfo(Instant takenAt, GeoPoint location) {

        public static CaptureInfo empty() {
            return new CaptureInfo(null, null);
        }
    }
}
