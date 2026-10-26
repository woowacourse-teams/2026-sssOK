package com.sssok.renewal.outgoing.push;

// 구독의 브라우저 공개키로 키 합의를 할 수 없다. 다시 보내도 실패하므로 구독을 만료로 본다.
public class InvalidSubscriptionKeyException extends RuntimeException {
    public InvalidSubscriptionKeyException(Throwable cause) {
        super("구독 공개키로 암호화할 수 없습니다", cause);
    }
}
