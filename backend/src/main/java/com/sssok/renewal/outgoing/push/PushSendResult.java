package com.sssok.renewal.outgoing.push;

public enum PushSendResult {
    SENT,
    // 다시 보내도 소용없는 구독(404·410 응답 등). 호출한 쪽이 지운다.
    EXPIRED,
    FAILED,
    // VAPID 키가 설정되지 않음
    SKIPPED
}
