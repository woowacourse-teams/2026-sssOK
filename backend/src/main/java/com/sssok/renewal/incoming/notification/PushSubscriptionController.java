package com.sssok.renewal.incoming.notification;

import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import com.sssok.renewal.application.notification.subscription.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방 알림", description = "방 참여자에게 가는 브라우저 알림(Web Push) 설정과 업로드 요청")
@RestController
@RequestMapping("/api/v1/push-subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {
    private final PushSubscriptionService pushSubscriptionService;

    @Operation(
        summary = "VAPID 공개키 조회",
        description = "브라우저가 PushManager.subscribe({ applicationServerKey }) 에 넘길 공개키(base64url)를 받는다. "
            + "서버에 VAPID 키가 설정되지 않았으면 503."
    )
    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey(@Parameter(hidden = true) @AuthMember Long memberId) {
        return ApiResponse.of(new PushPublicKeyResponse(pushSubscriptionService.publicKey()));
    }

    @Operation(
        summary = "브라우저 알림 구독 등록",
        description = "PushSubscription.toJSON() 결과를 그대로 보낸다. 같은 endpoint 로 다시 보내면 덮어쓴다"
            + "(다른 계정으로 바꿔 로그인했어도 마지막 계정으로 옮겨진다). 알려진 브라우저 푸시 서비스"
            + "(FCM·Mozilla·Apple·Windows)의 https 주소가 아니거나 키 형식이 틀리면 400."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Valid @RequestBody RegisterPushSubscriptionRequest request
    ) {
        pushSubscriptionService.register(request.toCommand(memberId));
    }

    @Operation(
        summary = "브라우저 알림 구독 해제",
        description = "본인 구독만 지운다. 이미 없으면 아무 일도 하지 않고 204."
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Valid @RequestBody UnregisterPushSubscriptionRequest request
    ) {
        pushSubscriptionService.unregister(memberId, request.endpoint());
    }
}
