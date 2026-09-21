package com.sssok.domain.admin;

import com.sssok.domain.admin.exception.InvalidAdminNameException;

public record AdminName(String value) {

    private static final int MAX_LENGTH = 20;

    public AdminName {
        if (value == null || value.isBlank()) {
            throw new InvalidAdminNameException();
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new InvalidAdminNameException();
        }
    }
}
