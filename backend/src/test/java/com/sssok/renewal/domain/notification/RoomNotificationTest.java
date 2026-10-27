package com.sssok.renewal.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomNotificationTest {

    @Test
    void 업로드_알림은_장수와_방_갤러리_주소를_싣는다() {
        RoomNotification notification = RoomNotification.mediaUploaded(7L, "ABC123", "제주 여행", "민수", 3);

        assertThat(notification.type()).isEqualTo(RoomNotificationType.MEDIA_UPLOADED);
        assertThat(notification.roomId()).isEqualTo(7L);
        assertThat(notification.title()).isEqualTo("제주 여행");
        assertThat(notification.body()).isEqualTo("민수님이 사진 3장을 올렸어요");
        assertThat(notification.url()).isEqualTo("/rooms/ABC123/gallery");
    }

    @Test
    void 한_장이면_장수를_적지_않는다() {
        assertThat(RoomNotification.mediaUploaded(7L, "ABC123", "제주 여행", "민수", 1).body())
            .isEqualTo("민수님이 사진을 올렸어요");
    }

    @Test
    void 업로드_요청_알림() {
        RoomNotification notification = RoomNotification.uploadRequested(7L, "ABC123", "민수");

        assertThat(notification.type()).isEqualTo(RoomNotificationType.UPLOAD_REQUESTED);
        assertThat(notification.title()).isEqualTo("민수님이 알림을 보냈습니다");
        assertThat(notification.body()).isEqualTo("민수님의 사진을 올려주세요!");
        assertThat(notification.url()).isEqualTo("/rooms/ABC123/gallery");
    }

    @Test
    void 이름을_모르면_참여자로_부른다() {
        assertThat(RoomNotification.uploadRequested(7L, "ABC123", null).title())
            .isEqualTo("참여자님이 알림을 보냈습니다");
    }
}
