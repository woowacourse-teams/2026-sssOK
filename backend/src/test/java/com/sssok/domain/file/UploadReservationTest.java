package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import com.sssok.domain.file.exception.UploadAlreadyCompletedException;
import com.sssok.domain.file.exception.UploadRetryExceededException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UploadReservationTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
    private static final Long ROOM_ID = 1L;
    private static final Long UPLOADER_ID = 100L;
    private static final int MAX_RETRY = 5;

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
    class 재발급 {

        @Test
        void 재발급하면_횟수가_오르고_기준_시각이_갱신된다() {
            StoredFile file = reserved();
            Instant later = NOW.plus(Duration.ofMinutes(20));

            file.reissueUploadUrl(MAX_RETRY, later);

            assertThat(file.getRetryCount()).isEqualTo(1);
            // 정리 배치가 이 값을 보므로, 재시도 중인 파일이 회수되지 않으려면 미뤄져야 한다.
            assertThat(file.getReservedAt()).isEqualTo(later);
        }

        @Test
        void 실패한_업로드도_재발급할_수_있다() {
            StoredFile file = reserved();
            file.failUpload();

            file.reissueUploadUrl(MAX_RETRY, NOW);

            assertThat(file.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 이미_등록된_업로드는_재발급할_수_없다() {
            StoredFile file = reserved();
            file.startProcessing();

            // 이미 올라간 파일을 덮어쓰지 못하게 막는다.
            assertThatThrownBy(() -> file.reissueUploadUrl(MAX_RETRY, NOW))
                .isInstanceOf(UploadAlreadyCompletedException.class);
        }

        @Test
        void 완료된_업로드도_재발급할_수_없다() {
            StoredFile file = reserved();
            file.startProcessing();
            file.markReady();

            assertThatThrownBy(() -> file.reissueUploadUrl(MAX_RETRY, NOW))
                .isInstanceOf(UploadAlreadyCompletedException.class);
        }

        @Test
        void 한도만큼_쓰면_더_재발급할_수_없다() {
            StoredFile file = reserved();
            for (int i = 0; i < MAX_RETRY; i++) {
                file.reissueUploadUrl(MAX_RETRY, NOW);
            }

            assertThatThrownBy(() -> file.reissueUploadUrl(MAX_RETRY, NOW))
                .isInstanceOf(UploadRetryExceededException.class);
            assertThat(file.getRetryCount()).isEqualTo(MAX_RETRY);
        }

        @Test
        void 재압축해서_크기가_바뀌면_반영한다() {
            StoredFile file = reserved();

            file.changeFileSize(FileSize.ofKilobytes(500));

            assertThat(file.getFileSize().bytes()).isEqualTo(500 * 1024);
        }

        @Test
        void 바뀐_크기가_한도를_넘으면_예외() {
            StoredFile file = reserved();

            assertThatThrownBy(() -> file.changeFileSize(FileSize.ofMegabytes(11)))
                .isInstanceOf(FileSizeExceededException.class);
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
