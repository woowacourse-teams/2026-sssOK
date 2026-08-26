package com.sssok.domain.member;

import com.sssok.domain.member.exception.InvalidNicknameException;

public record Nickname(String value) {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 12;

    public Nickname {
        if (value == null) {
            throw new InvalidNicknameException();
        }
        value = value.trim();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidNicknameException();
        }
    }
}
