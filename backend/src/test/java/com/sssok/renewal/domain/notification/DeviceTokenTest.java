package com.sssok.renewal.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.notification.exception.InvalidDeviceTokenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceTokenTest {

    private static final String FCM_TOKEN = "dX3kq9_Rz-A:APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ";

    @Test
    void FCM_토큰을_받는다() {
        DeviceToken deviceToken = DeviceToken.create(1L, FCM_TOKEN, DevicePlatform.IOS);

        assertThat(deviceToken.getToken()).isEqualTo(FCM_TOKEN);
        assertThat(deviceToken.getPlatform()).isEqualTo(DevicePlatform.IOS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc def", "abc/def", "abc\"}"})
    void 형식이_틀린_토큰은_거절한다(String token) {
        assertThatThrownBy(() -> DeviceToken.create(1L, token, DevicePlatform.ANDROID))
            .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @Test
    void 너무_긴_토큰은_거절한다() {
        assertThatThrownBy(() -> DeviceToken.create(1L, "a".repeat(513), DevicePlatform.ANDROID))
            .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ios", "IOS", "android", "Android"})
    void 플랫폼은_대소문자를_가리지_않는다(String value) {
        assertThat(DevicePlatform.from(value)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"web", "windows"})
    void 지원하지_않는_플랫폼은_거절한다(String value) {
        assertThatThrownBy(() -> DevicePlatform.from(value))
            .isInstanceOf(InvalidDeviceTokenException.class);
    }
}
