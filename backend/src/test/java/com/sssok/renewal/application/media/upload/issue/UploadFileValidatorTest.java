package com.sssok.renewal.application.media.upload.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.outgoing.config.UploadProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class UploadFileValidatorTest {
    private final UploadFileValidator validator = new UploadFileValidator(
        new UploadProperties(Duration.ofMinutes(10), 3, DataSize.ofBytes(100), DataSize.ofBytes(200)));

    @Test
    void MIME을_정규화하고_정확한_경계_크기까지_예약한다() {
        Instant beforeValidation = Instant.now();
        StoredFile file = validate(new UploadCandidate("a.png", " IMAGE/JPEG ", 100L)).acceptedFiles().getFirst();
        assertThat(file.getMediaType().contentType()).isEqualTo("image/jpeg");
        assertThat(file.getStorageKey().value()).startsWith("rooms/1/").endsWith(".jpg");
        assertThat(file.getStatus()).isEqualTo(UploadStatus.RESERVED);
        assertThat(file.getReservedAt()).isBetween(beforeValidation, Instant.now());
    }

    @Test
    void 이미지와_영상은_서로_다른_크기_상한을_적용한다() {
        ValidatedUploadFiles result = validate(new UploadCandidate("a.jpg", "image/jpeg", 101L),
            new UploadCandidate("a.mp4", "video/mp4", 200L));
        assertThat(result.acceptedFiles()).extracting(StoredFile::getOriginalFileName).containsExactly("a.mp4");
        assertThat(result.rejectedFiles()).containsExactly(
            new RejectedFile("a.jpg", "FILE_TOO_LARGE", "파일 용량이 허용 크기를 초과했습니다"));
    }

    @Test
    void 필수_정보와_양수_크기_오류는_같은_공개_메시지를_갖는다() {
        ValidatedUploadFiles result = validate(new UploadCandidate(" ", "image/jpeg", 1L),
            new UploadCandidate("a.jpg", "image/jpeg", 0L), new UploadCandidate("b.jpg", "image/jpeg", null));
        assertThat(result.acceptedFiles()).isEmpty();
        assertThat(result.rejectedFiles()).hasSize(3).allSatisfy(rejected -> {
            assertThat(rejected.code()).isEqualTo("INVALID_PARAM");
            assertThat(rejected.message()).isEqualTo("파일 정보가 올바르지 않습니다");
        });
    }

    @Test
    void 지원하지_않는_타입은_예외가_거절_메시지를_소유한다() {
        assertThat(validate(new UploadCandidate("a.pdf", "application/pdf", 1L)).rejectedFiles())
            .containsExactly(new RejectedFile("a.pdf", "UNSUPPORTED_MEDIA_TYPE", "이미지와 영상만 업로드할 수 있습니다"));
    }

    private ValidatedUploadFiles validate(UploadCandidate... files) {
        return validator.validate(new IssueUploadUrlsCommand(1L, 2L, List.of(files), null));
    }
}
