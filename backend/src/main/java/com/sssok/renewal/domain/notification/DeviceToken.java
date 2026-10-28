package com.sssok.renewal.domain.notification;

import com.sssok.renewal.domain.notification.exception.InvalidDeviceTokenException;
import java.util.regex.Pattern;
import lombok.Getter;

// 앱이 FCM 에서 받은 기기 등록 토큰. 서버는 이 토큰을 FCM 에 넘기기만 하고 주소로 쓰지 않는다.
@Getter
public class DeviceToken {
    private static final int MAX_TOKEN_LENGTH = 512;
    // FCM 토큰은 영문·숫자와 : _ - 로만 이뤄진다.
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9:_-]+");

    private final Long memberId;
    private final String token;
    private final DevicePlatform platform;

    private DeviceToken(Long memberId, String token, DevicePlatform platform) {
        this.memberId = memberId;
        this.token = token;
        this.platform = platform;
    }

    public static DeviceToken create(Long memberId, String token, DevicePlatform platform) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH || !TOKEN_PATTERN.matcher(token).matches()) {
            throw new InvalidDeviceTokenException("token 값이 올바르지 않습니다");
        }
        return new DeviceToken(memberId, token, platform);
    }

    public static DeviceToken reconstruct(Long memberId, String token, DevicePlatform platform) {
        return new DeviceToken(memberId, token, platform);
    }
}
