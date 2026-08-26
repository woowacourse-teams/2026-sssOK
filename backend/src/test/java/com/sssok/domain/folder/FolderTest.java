package com.sssok.domain.folder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FolderTest {

    private final Instant now = Instant.now();

    @Test
    void 방_ID와_이름으로_생성된다() {
        Folder folder = Folder.create(1L, new FolderName("맛집"), now);

        assertThat(folder.getRoomId()).isEqualTo(1L);
        assertThat(folder.getName().value()).isEqualTo("맛집");
        assertThat(folder.getCreatedAt()).isEqualTo(now);
        assertThat(folder.getId()).isNull();
    }

    @Test
    void 이름을_바꿀_수_있다() {
        Folder folder = Folder.create(1L, new FolderName("맛집"), now);

        folder.rename(new FolderName("카페"));

        assertThat(folder.getName().value()).isEqualTo("카페");
    }

    @Test
    void 자신이_속한_방인지_확인할_수_있다() {
        Folder folder = Folder.reconstruct(10L, 1L, new FolderName("맛집"), now);

        assertThat(folder.belongsTo(1L)).isTrue();
        assertThat(folder.belongsTo(2L)).isFalse();
    }
}
