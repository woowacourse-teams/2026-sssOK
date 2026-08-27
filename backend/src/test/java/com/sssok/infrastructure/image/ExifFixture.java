package com.sssok.infrastructure.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

// EXIF 가 든 JPEG 을 만들어 주는 테스트 픽스처.
//
// EXIF 를 "쓰는" 라이브러리를 따로 들이지 않으려고 APP1 세그먼트를 직접 조립한다.
// 읽기만 하는 metadata-extractor 로는 만들 수 없고, 실제 사진을 저장소에 넣으면 바이너리가
// 커밋에 섞인다.
final class ExifFixture {

    // TIFF 헤더 기준 오프셋. 아래 배치를 그대로 옮긴 값이라 순서를 바꾸면 함께 고쳐야 한다.
    //   0 TIFF 헤더(8) | 8 IFD0(2+12*2+4=30) | 38 Exif IFD(2+12+4=18)
    //   56 GPS IFD(2+12*4+4=54) | 110 촬영시각 문자열(20) | 130 위도(24) | 154 경도(24)
    private static final int IFD0_OFFSET = 8;
    private static final int EXIF_IFD_OFFSET = 38;
    private static final int GPS_IFD_OFFSET = 56;
    private static final int DATETIME_OFFSET = 110;
    private static final int LATITUDE_OFFSET = 130;
    private static final int LONGITUDE_OFFSET = 154;

    private static final short TYPE_ASCII = 2;
    private static final short TYPE_LONG = 4;
    private static final short TYPE_RATIONAL = 5;

    private static final short TAG_EXIF_IFD_POINTER = (short) 0x8769;
    private static final short TAG_GPS_IFD_POINTER = (short) 0x8825;
    private static final short TAG_DATETIME_ORIGINAL = (short) 0x9003;
    private static final short TAG_GPS_LATITUDE_REF = 0x0001;
    private static final short TAG_GPS_LATITUDE = 0x0002;
    private static final short TAG_GPS_LONGITUDE_REF = 0x0003;
    private static final short TAG_GPS_LONGITUDE = 0x0004;

    // 초 단위를 10배로 저장해 소수 좌표를 정수 분수로 정확히 표현한다.
    private static final int SECONDS_DENOMINATOR = 10;

    private ExifFixture() {
    }

    static byte[] app1Segment(String dateTimeOriginal, double latitude, double longitude) {
        byte[] tiff = tiff(dateTimeOriginal, latitude, longitude);
        ByteBuffer segment = ByteBuffer.allocate(4 + 6 + tiff.length);
        segment.putShort((short) 0xFFE1);
        // 길이 필드는 자기 자신(2바이트)부터 센다. 마커 2바이트는 제외다.
        segment.putShort((short) (2 + 6 + tiff.length));
        segment.put("Exif".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0);
        segment.put(tiff);
        return segment.array();
    }

    private static byte[] tiff(String dateTimeOriginal, double latitude, double longitude) {
        ByteBuffer buffer = ByteBuffer.allocate(LONGITUDE_OFFSET + 24);

        // 빅엔디언("MM") + 매직 42 + IFD0 위치
        buffer.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(IFD0_OFFSET);

        buffer.putShort((short) 2);
        entry(buffer, TAG_EXIF_IFD_POINTER, TYPE_LONG, 1, EXIF_IFD_OFFSET);
        entry(buffer, TAG_GPS_IFD_POINTER, TYPE_LONG, 1, GPS_IFD_OFFSET);
        buffer.putInt(0);

        buffer.putShort((short) 1);
        entry(buffer, TAG_DATETIME_ORIGINAL, TYPE_ASCII, 20, DATETIME_OFFSET);
        buffer.putInt(0);

        buffer.putShort((short) 4);
        // 4바이트에 들어가는 값은 오프셋 자리에 그대로 넣는다("N\0" + 남는 자리 0).
        entry(buffer, TAG_GPS_LATITUDE_REF, TYPE_ASCII, 2, 'N' << 24);
        entry(buffer, TAG_GPS_LATITUDE, TYPE_RATIONAL, 3, LATITUDE_OFFSET);
        entry(buffer, TAG_GPS_LONGITUDE_REF, TYPE_ASCII, 2, 'E' << 24);
        entry(buffer, TAG_GPS_LONGITUDE, TYPE_RATIONAL, 3, LONGITUDE_OFFSET);
        buffer.putInt(0);

        buffer.put(ascii(dateTimeOriginal, 20));
        degreesMinutesSeconds(buffer, latitude);
        degreesMinutesSeconds(buffer, longitude);
        return buffer.array();
    }

    private static void entry(ByteBuffer buffer, short tag, short type, int count, int value) {
        buffer.putShort(tag).putShort(type).putInt(count).putInt(value);
    }

    // 도·분은 0 으로 두고 나머지를 전부 초로 표현한다. 분까지 쪼개면 반올림 오차가 생긴다.
    private static void degreesMinutesSeconds(ByteBuffer buffer, double coordinate) {
        int degrees = (int) coordinate;
        long seconds = Math.round((coordinate - degrees) * 3600 * SECONDS_DENOMINATOR);
        buffer.putInt(degrees).putInt(1);
        buffer.putInt(0).putInt(1);
        buffer.putInt((int) seconds).putInt(SECONDS_DENOMINATOR);
    }

    private static byte[] ascii(String value, int length) {
        byte[] padded = new byte[length];
        byte[] raw = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, length - 1));
        return padded;
    }
}
