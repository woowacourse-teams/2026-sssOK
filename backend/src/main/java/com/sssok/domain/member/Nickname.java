package com.sssok.domain.member;

import com.sssok.domain.member.exception.InvalidNicknameException;

public record Nickname(String value) {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 12;

    public Nickname {
        if (value == null) {
            throw new InvalidNicknameException("닉네임은 필수입니다.");
        }
        value = value.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidNicknameException(
                    "닉네임은 %d자 이상 %d자 이하여야 합니다.".formatted(MIN_LENGTH, MAX_LENGTH));
        }
    }
}
