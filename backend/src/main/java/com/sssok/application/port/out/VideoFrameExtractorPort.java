package com.sssok.application.port.out;

import com.sssok.domain.file.GeoPoint;
import java.time.Instant;

// 영상에서 썸네일로 쓸 프레임 한 장을 뽑는 출력. 어떤 도구로 뽑는지는 응용 계층이 알 필요가 없다.
//
// 파일이 아니라 주소를 받는다. 영상은 최대 1GB 라, 이미지처럼 통째로 받아 넘기면 힙이 터진다.
// 주소를 넘기면 추출기가 필요한 구간만 Range 로 골라 읽는다 — 이 인터페이스가 스토리지를
// 몰라도 되는 것은 덤이다.
public interface VideoFrameExtractorPort {

    // 예외 대신 결과로 돌려준다. 영상 하나 때문에 워커가 멈추지 않게 하려는 것으로,
    // ImageProcessorPort.shrink() 와 같은 방식이다.
    ExtractionResult extractFirstFrame(String sourceUrl, int maxWidth);

    // 실패를 두 갈래로 나눈다. 호출부가 상태를 확정할지 말지가 여기서 갈린다.
    //  - unreadable: 깨진 영상·못 읽는 코덱·추출기가 없는 환경. 다시 해도 같으니 썸네일 없이 완료.
    //  - retryLater: 원본에 닿지 못했거나 시간이 넘었거나 스레드가 끊겼다. 멀쩡한 영상을 썸네일
    //    없이 굳히지 않도록 PROCESSING 에 남겨 다시 태운다.
    record ExtractionResult(ExtractedFrame frame, boolean retryable) {

        public static ExtractionResult extracted(ExtractedFrame frame) {
            return new ExtractionResult(frame, false);
        }

        public static ExtractionResult unreadable() {
            return new ExtractionResult(null, false);
        }

        public static ExtractionResult retryLater() {
            return new ExtractionResult(null, true);
        }

        public boolean hasFrame() {
            return frame != null;
        }
    }

    // width/height 는 썸네일이 아니라 원본 영상의 크기다. 클라이언트가 자리를 미리 잡는 데 쓴다.
    //
    // durationSeconds·takenAt·location 은 컨테이너가 적어두지 않았으면 각각 비어 있다.
    // 사진의 EXIF 에 해당하는 값들이라, 없다고 썸네일까지 포기하지는 않는다.
    record ExtractedFrame(int width, int height, Integer durationSeconds,
                          Instant takenAt, GeoPoint location, byte[] content) {
    }
}
