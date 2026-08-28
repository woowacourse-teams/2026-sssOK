package com.sssok.application.port.out;

import com.sssok.domain.file.GeoPoint;
import java.time.Instant;
import java.util.Optional;

// 이미지 축소 출력. 어떤 라이브러리로 줄이는지는 응용 계층이 알 필요가 없다.
public interface ImageProcessorPort {

    // 원본 크기와 축소본을 한 번에 돌려준다. 디코딩이 가장 비싼 일이라 두 번 읽지 않는다.
    // 손상됐거나 읽을 수 없는 형식이면 비어 있다 — 예외 대신 빈 값으로 두어, 사진 한 장 때문에
    // 배치가 멈추지 않게 한다.
    Optional<ProcessedImage> shrink(byte[] source, int maxWidth, String format);

    // 촬영 시각과 좌표. 카메라가 남기지 않았거나 편집 과정에서 지워졌으면 각각 비어 있다.
    // 축소와 나눈 이유는, EXIF 를 못 읽는다고 썸네일까지 포기할 이유가 없어서다.
    CaptureInfo readCaptureInfo(byte[] source);

    record ProcessedImage(int sourceWidth, int sourceHeight, byte[] content) {
    }

    record CaptureInfo(Instant takenAt, GeoPoint location) {

        public static CaptureInfo empty() {
            return new CaptureInfo(null, null);
        }
    }
}
