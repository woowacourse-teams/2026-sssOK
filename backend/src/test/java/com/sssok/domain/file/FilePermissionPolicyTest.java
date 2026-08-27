package com.sssok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilePermissionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final Long ME = 100L;
    private static final Long OTHER = 200L;

    private static final boolean HOST = true;
    private static final boolean NOT_HOST = false;

    private static StoredFile fileOf(Long uploaderId) {
        return StoredFile.reserve(1L, uploaderId, "cat.png", "image/png", FileSize.ofMegabytes(1), NOW);
    }

    @Test
    void 본인이_올린_파일은_본인이_삭제할_수_있다() {
        StoredFile myFile = fileOf(ME);

        assertThat(FilePermissionPolicy.canDelete(myFile, ME, NOT_HOST)).isTrue();
    }

    @Test
    void 남이_올린_파일은_삭제할_수_없다() {
        StoredFile othersFile = fileOf(OTHER);

        assertThat(FilePermissionPolicy.canDelete(othersFile, ME, NOT_HOST)).isFalse();
    }

    @Test
    void 방장은_남이_올린_파일도_삭제할_수_있다() {
        StoredFile othersFile = fileOf(OTHER);

        assertThat(FilePermissionPolicy.canDelete(othersFile, ME, HOST)).isTrue();
    }

    @Test
    void 선택_삭제할_때_권한_없는_항목만_빼고_나머지는_처리한다() {
        StoredFile mine = fileOf(ME);
        StoredFile others = fileOf(OTHER);
        StoredFile mineAgain = fileOf(ME);

        List<StoredFile> deletable =
            FilePermissionPolicy.filterDeletable(List.of(mine, others, mineAgain), ME, NOT_HOST);

        assertThat(deletable).containsExactly(mine, mineAgain);
    }

    @Test
    void 방장이_선택_삭제하면_전부_대상이_된다() {
        StoredFile mine = fileOf(ME);
        StoredFile others = fileOf(OTHER);

        List<StoredFile> deletable =
            FilePermissionPolicy.filterDeletable(List.of(mine, others), ME, HOST);

        assertThat(deletable).containsExactly(mine, others);
    }

    @Test
    void 전부_권한이_없으면_빈_목록이_된다() {
        StoredFile others = fileOf(OTHER);

        List<StoredFile> deletable =
            FilePermissionPolicy.filterDeletable(List.of(others), ME, NOT_HOST);

        assertThat(deletable).isEmpty();
    }
}