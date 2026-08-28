package com.sssok.domain.file;

import com.sssok.domain.file.exception.InvalidGeoPointException;
import java.math.BigDecimal;

// 사진이 찍힌 좌표. 둘 중 하나만 있는 좌표는 좌표가 아니라, 항상 쌍으로 다룬다.
public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public GeoPoint {
        if (latitude == null || longitude == null) {
            throw new InvalidGeoPointException("위도와 경도는 함께 있어야 합니다");
        }
        requireInRange(latitude, MIN_LATITUDE, MAX_LATITUDE, "위도");
        requireInRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "경도");
    }

    // 둘 다 있을 때만 좌표를 만든다. EXIF 가 한쪽만 남긴 사진이 실제로 있다.
    public static GeoPoint ofNullable(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return new GeoPoint(latitude, longitude);
    }

    private static void requireInRange(BigDecimal value, BigDecimal min, BigDecimal max,
                                       String name) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new InvalidGeoPointException("%s 가 범위를 벗어났습니다: %s".formatted(name, value));
        }
    }
}
