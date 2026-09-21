package com.sssok.application.port.out;

import com.sssok.domain.file.GeoPoint;
import java.time.Instant;
import java.util.Optional;

// 영상에서 썸네일로 쓸 프레임 한 장을 뽑는 출력. 어떤 도구로 뽑는지는 응용 계층이 알 필요가 없다.
//
// 파일이 아니라 주소를 받는다. 영상은 최대 1GB 라, 이미지처럼 통째로 받아 넘기면 힙이 터진다.
// 주소를 넘기면 추출기가 필요한 구간만 Range 로 골라 읽는다 — 이 인터페이스가 스토리지를
// 몰라도 되는 것은 덤이다.
public interface VideoFrameExtractorPort {

    // 깨졌거나 코덱을 읽지 못하면 비어 있다 — 예외 대신 빈 값으로 두어, 영상 하나 때문에
    // 워커가 멈추지 않게 한다. ImageProcessorPort.shrink() 와 같은 방식이다.
    Optional<ExtractedFrame> extractFirstFrame(String sourceUrl, int maxWidth);

    // width/height 는 썸네일이 아니라 원본 영상의 크기다. 클라이언트가 자리를 미리 잡는 데 쓴다.
    //
    // durationSeconds·takenAt·location 은 컨테이너가 적어두지 않았으면 각각 비어 있다.
    // 사진의 EXIF 에 해당하는 값들이라, 없다고 썸네일까지 포기하지는 않는다.
    record ExtractedFrame(int width, int height, Integer durationSeconds,
                          Instant takenAt, GeoPoint location, byte[] content) {
    }
}
