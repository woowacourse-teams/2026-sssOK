package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomCodeException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.random.RandomGenerator;

// 방 참여에 쓰이는 8자리 코드 값 객체.
public record RoomCode(String value) {

    // 혼동하기 쉬운 0, 1, I, O 는 제외한다 (QR·구두 전달 시 헷갈림 방지).
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LENGTH = 8;

    public RoomCode {
        if (value == null || !value.matches("[" + ALPHABET + "]{" + LENGTH + "}")) {
            throw new InvalidRoomCodeException(value);
        }
    }

    // 입장 자격 확인용 비교. 몇 번째 글자에서 틀렸는지 응답 시간으로 드러나지 않게 끝까지 비교한다.
    public boolean matches(RoomCode other) {
        return other != null && MessageDigest.isEqual(
            value.getBytes(StandardCharsets.UTF_8),
            other.value.getBytes(StandardCharsets.UTF_8));
    }

    public static RoomCode generate(RandomGenerator random) {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new RoomCode(builder.toString());
    }
}
