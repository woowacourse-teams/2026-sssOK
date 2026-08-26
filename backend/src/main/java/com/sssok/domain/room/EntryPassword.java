package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidEntryPasswordException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 방 입장 암호 값 객체. 평문은 저장하지 않고 SHA-256 해시만 보관한다.
public record EntryPassword(String hash) {

    private static final String ALGORITHM = "SHA-256";
    private static final String HASH_FORMAT = "[0-9a-f]{64}";
    private static final int MIN_RAW_LENGTH = 4;
    private static final int MAX_RAW_LENGTH = 20;

    public EntryPassword {
        if (hash == null || !hash.matches(HASH_FORMAT)) {
            throw new InvalidEntryPasswordException("입장 암호 해시 형식이 올바르지 않습니다.");
        }
    }

    public static EntryPassword of(String rawPassword, RoomCode salt) {
        return new EntryPassword(hash(normalize(rawPassword), salt));
    }

    public boolean matches(String rawPassword, RoomCode salt) {
        if (rawPassword == null) {
            return false;
        }
        return MessageDigest.isEqual(
            hash.getBytes(StandardCharsets.UTF_8),
            hash(rawPassword.trim(), salt).getBytes(StandardCharsets.UTF_8));
    }

    // 앞뒤 공백은 길이에 세지 않는다. matches 도 같은 기준으로 잘라 비교한다.
    private static String normalize(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidEntryPasswordException("입장 암호는 비어 있을 수 없습니다.");
        }
        String trimmed = rawPassword.trim();
        if (trimmed.length() < MIN_RAW_LENGTH || trimmed.length() > MAX_RAW_LENGTH) {
            throw new InvalidEntryPasswordException(
                "입장 암호는 %d자 이상 %d자 이하여야 합니다.".formatted(MIN_RAW_LENGTH, MAX_RAW_LENGTH));
        }
        return trimmed;
    }

    private static String hash(String rawPassword, RoomCode salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest((rawPassword + salt.value()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("%s 알고리즘을 사용할 수 없습니다.".formatted(ALGORITHM), e);
        }
    }
}
