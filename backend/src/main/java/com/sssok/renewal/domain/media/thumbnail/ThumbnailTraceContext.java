package com.sssok.renewal.domain.media.thumbnail;

import java.util.regex.Pattern;

/** DB 큐를 건너는 W3C 문맥만 보관한다. 사용자 정보나 baggage는 저장하지 않는다. */
public record ThumbnailTraceContext(String traceparent, String tracestate) {
    private static final Pattern TRACEPARENT = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    public static final ThumbnailTraceContext EMPTY = new ThumbnailTraceContext(null, null);

    public ThumbnailTraceContext {
        if (traceparent == null || !TRACEPARENT.matcher(traceparent).matches()
            || traceparent.substring(3, 35).equals("0".repeat(32))
            || traceparent.substring(36, 52).equals("0".repeat(16))) {
            traceparent = null;
            tracestate = null;
        }
        if (tracestate != null && tracestate.length() > 512) {
            tracestate = null;
        }
    }
}
