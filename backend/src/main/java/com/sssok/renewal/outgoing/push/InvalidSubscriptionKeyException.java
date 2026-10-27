package com.sssok.renewal.outgoing.push;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 구독의 브라우저 공개키로 키 합의를 할 수 없다. 다시 보내도 실패하므로 구독을 만료로 본다.
// WebPushClient 안에서만 잡히고 응답으로 나가지 않는다.
public class InvalidSubscriptionKeyException extends SssOkException {
    public InvalidSubscriptionKeyException(Throwable cause) {
        super("구독 공개키로 암호화할 수 없습니다", ErrorCode.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
