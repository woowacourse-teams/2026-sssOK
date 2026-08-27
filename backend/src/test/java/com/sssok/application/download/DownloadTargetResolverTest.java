package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DownloadTargetResolverTest extends PostgresContainerSupport {

    @Autowired
    DownloadTargetResolver downloadTargetResolver;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private void media(long id, long roomId, String status) {
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, id, roomId, "test-key-" + id, status);
    }

    @Nested
    class mediaIds_모드 {

        @Test
        void 요청한_미디어를_그대로_돌려준다() {
            media(1L, 1L, "READY");
            media(2L, 1L, "READY");

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(1L, 2L), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        void 다른_방_미디어는_조용히_빠진다() {
            media(1L, 1L, "READY");
            media(2L, 2L, "READY");

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(1L, 2L), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(1L);
        }

        @Test
        void READY가_아닌_미디어는_대상에서_빠진다() {
            media(1L, 1L, "READY");
            media(2L, 1L, "PROCESSING");

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(1L, 2L), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(1L);
        }

        @Test
        void 요청한_id가_전부_존재하지만_전부_READY가_아니면_404() {
            media(1L, 1L, "PROCESSING");

            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, List.of(1L), null))
                .isInstanceOf(MediaNotFoundException.class);
        }

        @Test
        void 요청한_id가_전부_존재하지_않으면_404() {
            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, List.of(999L), null))
                .isInstanceOf(MediaNotFoundException.class);
        }

        @Test
        void 개수가_상한을_초과하면_예외() {
            List<Long> tooMany = LongStream.rangeClosed(1, 1001).boxed().toList();

            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, tooMany, null))
                .isInstanceOf(TooManyFilesException.class);
        }
    }

    @Nested
    class folderId_모드 {

        @Test
        void 폴더에_담긴_미디어_중_READY만_돌려준다() {
            Folder folder = createFolderService.create(1L, "맛집");
            media(1L, 1L, "READY");
            media(2L, 1L, "PROCESSING");
            folderMediaRepository.attachToFolder(folder.getId(), List.of(1L, 2L));

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, null, folder.getId());

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(1L);
        }

        @Test
        void 없는_폴더면_예외() {
            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, null, -1L))
                .isInstanceOf(FolderNotFoundException.class);
        }

        @Test
        void 다른_방_소속_폴더는_없는_폴더로_취급한다() {
            Folder folder = createFolderService.create(2L, "맛집");

            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, null, folder.getId()))
                .isInstanceOf(FolderNotFoundException.class);
        }
    }

    @Nested
    class 둘_다_생략하면_방_전체 {

        @Test
        void 방의_READY_미디어_전체를_돌려준다() {
            media(1L, 1L, "READY");
            media(2L, 1L, "READY");
            media(3L, 1L, "PROCESSING");
            media(4L, 2L, "READY");

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, null, null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Nested
    class 잘못된_조합 {

        @Test
        void mediaIds와_folderId를_동시에_주면_예외() {
            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, List.of(1L), 1L))
                .isInstanceOf(InvalidDownloadParamException.class);
        }
    }
}
