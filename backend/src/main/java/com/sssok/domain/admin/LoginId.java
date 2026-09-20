package com.sssok.domain.admin;

import com.sssok.domain.admin.exception.InvalidAdminLoginIdException;
import java.util.regex.Pattern;

public record LoginId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_]{4,20}$");

    public LoginId {
        if (value == null) {
            throw new InvalidAdminLoginIdException();
        }
        value = value.strip().toLowerCase();
        if (!PATTERN.matcher(value).matches()) {
            throw new InvalidAdminLoginIdException();
        }
    }
}
