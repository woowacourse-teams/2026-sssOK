package com.sssok.presentation.api.media;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sssok.application.media.MediaFullDetail;
import com.sssok.domain.file.GeoPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "미디어 단건 상세")
public record MediaDetailResponse(
    @JsonUnwrapped MediaResponse media,
    @Schema(description = "촬영 시각. 사진은 EXIF, 영상은 컨테이너 메타데이터에서 읽는다. "
        + "기기가 남기지 않았으면 null") Instant takenAt,
    @Schema(description = "촬영 좌표. 사진은 EXIF, 영상은 컨테이너 메타데이터에서 읽는다. "
        + "위치 기록이 꺼져 있었으면 null") LocationResponse location,
    @Schema(description = "요청자가 이 미디어를 지울 수 있는지. 올린 본인과 방장만 true")
    boolean canDelete
) {
    public static MediaDetailResponse from(MediaFullDetail detail) {
        return new MediaDetailResponse(
            MediaResponse.from(detail.media()), detail.takenAt(),
            LocationResponse.from(detail.location()), detail.canDelete());
    }

    @Schema(description = "촬영 위치")
    public record LocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        @Schema(description = "지명. 역지오코딩이 붙기 전까지 null") String name
    ) {
        static LocationResponse from(GeoPoint location) {
            if (location == null) {
                return null;
            }
            return new LocationResponse(location.latitude(), location.longitude(), null);
        }
    }
}
