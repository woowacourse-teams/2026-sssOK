package com.sssok.renewal.incoming.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.renewal.application.notification.request.UploadRequestedEvent;
import org.junit.jupiter.api.Test;

class UploadRequestedEventListenerTest {
    private final EventPublisherPort publisher = mock(EventPublisherPort.class);
    private final UploadRequestedEventListener listener = new UploadRequestedEventListener(publisher);
    private final UploadRequestedEvent event =
        new UploadRequestedEvent(7L, "ABC123", 3L, "민수님이 알림을 보냈습니다", "민수님의 사진을 올려주세요!");

    @Test
    void 방_전체_대상_upload_requested_이벤트로_내보낸다() {
        listener.handle(event);

        verify(publisher).publish(7L, "upload.requested", event);
    }

    @Test
    void SSE_발행이_실패해도_예외를_밖으로_던지지_않는다() {
        willThrow(new IllegalStateException("db down")).given(publisher).publish(anyLong(), anyString(), any());

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }
}
