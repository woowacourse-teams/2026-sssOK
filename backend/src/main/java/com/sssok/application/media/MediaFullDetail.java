package com.sssok.application.media;

import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.StoredFile;
import java.time.Instant;

// 단건 조회 전용. 목록의 항목에 촬영 정보와 삭제 권한을 더한 것이다.
//
// 목록에 함께 싣지 않는 이유는 canDelete 다. 이 값은 보는 사람에 따라 달라져서, 목록에 넣으면
// 같은 방의 사진 목록을 사용자마다 다르게 캐싱해야 한다.
// 보는 사람마다 다른 값 중 likedByMe 만은 예외로 목록에도 싣는다(#448). 좋아요순 정렬과
// "내가 좋아요한 사진" 모아보기를 프론트가 목록 응답만으로 처리해야 해서다.
public record MediaFullDetail(MediaDetail media, Instant takenAt, GeoPoint location,
                              boolean canDelete) {

    public static MediaFullDetail of(StoredFile file, MediaDetail media, boolean canDelete) {
        return new MediaFullDetail(media, file.getTakenAt(), file.getLocation(), canDelete);
    }
}
