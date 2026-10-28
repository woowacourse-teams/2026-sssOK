package com.sssok.renewal.domain.notification;

import com.sssok.renewal.domain.notification.exception.InvalidDeviceTokenException;
import java.util.Arrays;

public enum DevicePlatform {
    IOS,
    ANDROID;

    public static DevicePlatform from(String value) {
        return Arrays.stream(values())
            .filter(platform -> platform.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new InvalidDeviceTokenException("platform 은 ios 또는 android 만 쓸 수 있습니다"));
    }
}
