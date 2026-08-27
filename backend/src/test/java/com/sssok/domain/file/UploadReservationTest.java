package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UploadReservationTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
    private static final Long ROOM_ID = 1L;
    private static final Long UPLOADER_ID = 100L;

    private static StoredFile reserved() {
        return StoredFile.reserve(ROOM_ID, UPLOADER_ID, "cat.png", "image/png",
            FileSize.ofMegabytes(1), NOW);
    }

    @Nested
    class 예약 {

        @Test
        void MIME_으로_타입을_정한다() {
            StoredFile file = StoredFile.reserve(ROOM_ID, UPLOADER_ID, "cat.png", "image/jpeg",
                FileSize.ofMegabytes(1), NOW);

            // 확장자가 아니라 MIME 이 기준이다. 확장자는 위조하기 쉽다.
            assertThat(file.getMediaType()).isEqualTo(MediaType.JPEG);
        }

        @Test
        void 허용_목록에_없는_MIME_이면_예외() {
            assertThatThrownBy(() -> StoredFile.reserve(ROOM_ID, UPLOADER_ID, "note.pdf",
                "application/pdf", FileSize.ofMegabytes(1), NOW))
                .isInstanceOf(UnsupportedMediaTypeException.class);
        }

        @Test
        void 타입별_용량_한도를_넘으면_예외() {
            assertThatThrownBy(() -> StoredFile.reserve(ROOM_ID, UPLOADER_ID, "cat.png",
                "image/png", FileSize.ofMegabytes(11), NOW))
                .isInstanceOf(FileSizeExceededException.class);
        }

        @Test
        void 예약_상태로_시작하고_재시도_횟수는_0이다() {
            StoredFile file = reserved();

            assertThat(file.getStatus()).isEqualTo(UploadStatus.RESERVED);
            assertThat(file.getRetryCount()).isZero();
            assertThat(file.getReservedAt()).isEqualTo(NOW);
        }
    }


    @Nested
    class 실제_업로드_대조 {

        @Test
        void 크기와_MIME_이_모두_같아야_통과한다() {
            StoredFile file = reserved();

            assertThat(file.matchesUploaded(FileSize.ofMegabytes(1).bytes(), "image/png")).isTrue();
        }

        @Test
        void 크기가_다르면_거부한다() {
            StoredFile file = reserved();

            assertThat(file.matchesUploaded(FileSize.ofMegabytes(2).bytes(), "image/png")).isFalse();
        }

        @Test
        void MIME_이_다르면_거부한다() {
            StoredFile file = reserved();

            // 신고는 png 로 하고 실제로는 다른 것을 올리는 위조를 막는다.
            assertThat(file.matchesUploaded(FileSize.ofMegabytes(1).bytes(), "video/mp4")).isFalse();
        }
    }
}
