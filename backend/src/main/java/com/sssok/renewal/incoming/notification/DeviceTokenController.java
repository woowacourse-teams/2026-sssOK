package com.sssok.renewal.incoming.notification;

import com.sssok.renewal.incoming.auth.AuthMember;
import com.sssok.renewal.application.notification.subscription.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방 알림", description = "방 참여자에게 가는 브라우저·앱 알림 설정과 업로드 요청")
@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {
    private final DeviceTokenService deviceTokenService;

    @Operation(
        summary = "앱 기기 토큰 등록",
        description = "네이티브 앱이 FCM 에서 받은 등록 토큰을 보낸다. iOS 도 APNs 기기 토큰이 아니라 "
            + "FCM 등록 토큰이어야 한다(APNs 토큰은 FCM 이 거절해 알림이 가지 않는다). "
            + "로그인으로 계정이 바뀌면 다시 보내야 새 계정으로 알림이 간다. 같은 토큰으로 다시 보내면 덮어쓴다"
            + "(다른 계정으로 바꿔 로그인했어도 마지막 계정으로 옮겨진다). 토큰 형식이 틀리거나 "
            + "platform 이 ios·android 가 아니면 400."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        deviceTokenService.register(request.toCommand(memberId));
    }

    @Operation(
        summary = "앱 기기 토큰 해제",
        description = "본인 토큰만 지운다. 이미 없으면 아무 일도 하지 않고 204."
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Valid @RequestBody UnregisterDeviceTokenRequest request
    ) {
        deviceTokenService.unregister(memberId, request.token());
    }
}
