package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoredFileTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final Long ROOM_ID = 1L;
    private static final Long UPLOADER_ID = 100L;

    // 예약은 MIME 으로 타입을 정하므로, 파일명으로 쓰던 기존 테스트를 위해 확장자에서 뽑아 넘긴다.
    private static StoredFile beginUpload(String fileName, FileSize size, Long folderId) {
        StoredFile file = StoredFile.reserve(ROOM_ID, UPLOADER_ID, fileName,
            MediaType.fromFileName(fileName).contentType(), size, NOW);
        if (folderId != null) {
            file.moveToFolder(folderId);
        }
        return file;
    }

    private static StoredFile uploading() {
        StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);
        file.startProcessing();
        return file;
    }

    @Nested
    class 업로드_시작 {

        @Test
        void 대기_상태로_시작하고_스토리지_키가_발급된다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

            assertThat(file.getStatus()).isEqualTo(UploadStatus.RESERVED);
            assertThat(file.getMediaType()).isEqualTo(MediaType.PNG);
            assertThat(file.getStorageKey().value()).startsWith("rooms/1/").endsWith(".png");
        }

        @Test
        void 같은_이름을_두_번_올려도_스토리지_키는_겹치지_않는다() {
            StoredFile first = beginUpload("cat.png", FileSize.ofMegabytes(1), null);
            StoredFile second = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

            assertThat(first.getStorageKey()).isNotEqualTo(second.getStorageKey());
        }

        @Test
        void 폴더를_지정하지_않으면_루트에_저장된다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

            assertThat(file.isInRoot()).isTrue();
        }

        @Test
        void 폴더를_지정하면_그_폴더로_저장된다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), 7L);

            assertThat(file.getFolderId()).isEqualTo(7L);
            assertThat(file.isInRoot()).isFalse();
        }

        @Test
        void 이미지가_10MB_를_넘으면_예외() {
            assertThatThrownBy(() ->
                beginUpload("cat.png", FileSize.ofMegabytes(11), null))
                .isInstanceOf(FileSizeExceededException.class);
        }

        @Test
        void 영상은_1GB_까지_허용된다() {
            StoredFile file = beginUpload("clip.mp4", FileSize.ofMegabytes(1024), null);

            assertThat(file.getMediaType()).isEqualTo(MediaType.MP4);
        }

        @Test
        void 영상이_1GB_를_넘으면_예외() {
            assertThatThrownBy(() ->
                beginUpload("clip.mp4", FileSize.ofMegabytes(1025), null))
                .isInstanceOf(FileSizeExceededException.class);
        }

        @Test
        void 지원하지_않는_형식이면_예외() {
            assertThatThrownBy(() ->
                beginUpload("report.pdf", FileSize.ofMegabytes(1), null))
                .isInstanceOf(UnsupportedMediaTypeException.class);
        }
    }

    @Nested
    class 상태_전이 {

        @Test
        void 대기에서_업로드중을_거쳐_완료된다() {
            StoredFile file = uploading();
            file.markReady();

            assertThat(file.getStatus()).isEqualTo(UploadStatus.READY);
        }

        @Test
        void 업로드에_실패할_수_있다() {
            StoredFile file = uploading();
            file.failUpload();

            assertThat(file.getStatus()).isEqualTo(UploadStatus.FAILED);
        }

        @Test
        void 실패한_파일만_재시도할_수_있다() {
            StoredFile file = uploading();
            file.failUpload();

            file.startProcessing();

            assertThat(file.getStatus()).isEqualTo(UploadStatus.PROCESSING);
        }

        @Test
        void 완료된_파일은_재시도할_수_없다() {
            StoredFile file = uploading();
            file.markReady();

            assertThatThrownBy(file::startProcessing)
                .isInstanceOf(IllegalUploadStatusException.class);
        }

        @Test
        void 대기_상태에서_바로_완료할_수_없다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

            assertThatThrownBy(file::markReady)
                .isInstanceOf(IllegalUploadStatusException.class);
        }

        @Test
        void 완료된_파일은_다시_실패할_수_없다() {
            StoredFile file = uploading();
            file.markReady();

            assertThatThrownBy(file::failUpload)
                .isInstanceOf(IllegalUploadStatusException.class);
        }
    }

    @Nested
    class 폴더_이동 {

        @Test
        void 기존_폴더로_이동할_수_있다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

            file.moveToFolder(7L);

            assertThat(file.getFolderId()).isEqualTo(7L);
        }

        @Test
        void 다른_폴더로_옮길_수_있다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), 7L);

            file.moveToFolder(8L);

            assertThat(file.getFolderId()).isEqualTo(8L);
        }

        @Test
        void 폴더에서_꺼내면_루트로_간다() {
            StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), 7L);

            file.moveToRoot();

            assertThat(file.isInRoot()).isTrue();
        }
    }

    @Test
    void 업로더_본인인지_판별할_수_있다() {
        StoredFile file = beginUpload("cat.png", FileSize.ofMegabytes(1), null);

        assertThat(file.isUploadedBy(UPLOADER_ID)).isTrue();
        assertThat(file.isUploadedBy(999L)).isFalse();
    }

    @Test
    void 자신의_최적화_계획을_알려준다() {
        StoredFile gif = beginUpload("anim.gif", FileSize.ofMegabytes(5), null);
        StoredFile png = beginUpload("cat.png", FileSize.ofMegabytes(5), null);

        assertThat(gif.optimizationPlan().skipped()).isTrue();
        assertThat(png.optimizationPlan().skipped()).isFalse();
    }
}
