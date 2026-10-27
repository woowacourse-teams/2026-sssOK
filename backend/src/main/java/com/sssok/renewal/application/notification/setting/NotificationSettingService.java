package com.sssok.renewal.application.notification.setting;

import com.sssok.renewal.outgoing.database.RoomNotificationSettingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {
    private final RoomNotificationSettingRepository settingRepository;

    public boolean isEnabled(Long roomId, Long memberId) {
        return settingRepository.isEnabled(roomId, memberId);
    }

    public boolean update(Long roomId, Long memberId, boolean enabled) {
        settingRepository.save(roomId, memberId, enabled, Instant.now());
        return enabled;
    }
}
