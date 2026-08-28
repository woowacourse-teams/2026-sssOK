package com.sssok.presentation.api.media;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sssok.application.media.MediaFullDetail;
import com.sssok.domain.file.GeoPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

// 목록 항목과 같은 필드를 그대로 펼치고(JsonUnwrapped) 단건 전용 항목만 덧붙인다.
// 따로 나열하면 목록과 단건의 공통 필드가 갈라져도 눈치채지 못한다.
@Schema(description = "미디어 단건 상세")
public record MediaFullResponse(
    @JsonUnwrapped MediaResponse media,
    @Schema(description = "EXIF 촬영 시각. 카메라가 남기지 않았으면 null") Instant takenAt,
    @Schema(description = "EXIF 좌표. 위치 기록이 꺼져 있었으면 null") LocationResponse location,
    @Schema(description = "요청자가 이 미디어를 지울 수 있는지. 올린 본인과 방장만 true")
    boolean canDelete
) {
    public static MediaFullResponse from(MediaFullDetail detail, String accessToken) {
        return new MediaFullResponse(
            MediaResponse.from(detail.media(), accessToken),
            detail.takenAt(),
            LocationResponse.from(detail.location()),
            detail.canDelete());
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
