package com.sssok.application.media;

import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.StoredFile;
import java.time.Instant;

// 단건 조회 전용. 목록의 항목에 촬영 정보와 삭제 권한을 더한 것이다.
//
// 목록에 함께 싣지 않는 이유는 canDelete 다. 이 값은 보는 사람에 따라 달라져서, 목록에 넣으면
// 같은 방의 사진 목록을 사용자마다 다르게 캐싱해야 한다.
public record MediaFullDetail(MediaDetail media, Instant takenAt, GeoPoint location,
                              boolean canDelete) {

    public static MediaFullDetail of(StoredFile file, MediaDetail media, boolean canDelete) {
        return new MediaFullDetail(media, file.getTakenAt(), file.getLocation(), canDelete);
    }
}
