package com.sssok.renewal.incoming.notification;

import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.incoming.auth.AuthMember;
import com.sssok.renewal.application.notification.request.RequestUploadService;
import com.sssok.renewal.application.notification.setting.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 방 입장 여부는 RoomMembershipInterceptor 가 컨트롤러 진입 전에 확인한다.
@Tag(name = "방 알림", description = "방 참여자에게 가는 브라우저 알림(Web Push) 설정과 업로드 요청")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomNotificationController {
    private final NotificationSettingService notificationSettingService;
    private final RequestUploadService requestUploadService;

    @Operation(
        summary = "방 알림 설정 조회",
        description = "이 방의 알림(업로드 알림·업로드 요청 알림)을 받는지 조회한다. 바꾼 적이 없으면 켜져 있다(true)."
    )
    @GetMapping("/notification-setting")
    public ApiResponse<NotificationSettingResponse> getSetting(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        return ApiResponse.of(new NotificationSettingResponse(notificationSettingService.isEnabled(roomId, memberId)));
    }

    @Operation(
        summary = "방 알림 켜기/끄기",
        description = "이 방의 알림을 켜거나 끈다. 끄면 업로드 알림과 업로드 요청 알림을 모두 받지 않는다. "
            + "설정은 방마다 따로 저장된다."
    )
    @PutMapping("/notification-setting")
    public ApiResponse<NotificationSettingResponse> updateSetting(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Valid @RequestBody NotificationSettingRequest request
    ) {
        boolean enabled = notificationSettingService.update(roomId, memberId, request.enabled());
        return ApiResponse.of(new NotificationSettingResponse(enabled));
    }

    @Operation(
        summary = "업로드 요청 보내기",
        description = "\"내 사진 올려 줘\" 요청을 보낸다. 알림을 켠 같은 방의 다른 참여자에게 브라우저 알림이 간다. "
            + "발송은 비동기라 요청을 접수하면 바로 202를 돌려준다. 같은 방에서 쿨다운(기본 1분) 안에 다시 요청하면 429."
    )
    @PostMapping("/upload-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestUpload(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        requestUploadService.request(roomId, memberId);
    }
}
