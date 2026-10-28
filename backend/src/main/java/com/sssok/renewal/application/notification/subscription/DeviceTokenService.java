package com.sssok.renewal.application.notification.subscription;

import com.sssok.renewal.domain.notification.DevicePlatform;
import com.sssok.renewal.domain.notification.DeviceToken;
import com.sssok.renewal.outgoing.database.DeviceTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 브라우저 구독과 마찬가지로 기기 토큰도 회원에 붙고, 어느 방 알림을 받을지는 방 참여 여부와 방별 설정으로 정한다.
@Service
@RequiredArgsConstructor
public class DeviceTokenService {
    private final DeviceTokenRepository deviceTokenRepository;

    public void register(RegisterDeviceTokenCommand command) {
        DeviceToken deviceToken = DeviceToken.create(
            command.memberId(), command.token(), DevicePlatform.from(command.platform()));
        deviceTokenRepository.save(deviceToken, Instant.now());
    }

    public void unregister(Long memberId, String token) {
        deviceTokenRepository.delete(memberId, token);
    }
}
