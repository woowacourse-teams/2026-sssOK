package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.download.exception.InvalidDownloadParamException;
import com.sssok.application.download.exception.TooManyFilesException;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    FileRepository fileRepository;

    private Long media(long roomId, UploadStatus status) {
        StoredFile file = StoredFile.reserve(roomId, 1L, "test.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        switch (status) {
            case PROCESSING -> file.startProcessing();
            case READY -> {
                file.startProcessing();
                file.markReady();
            }
            case FAILED -> file.failUpload();
            default -> {
            }
        }
        return fileRepository.save(file).getId();
    }

    @Nested
    class mediaIds_모드 {

        @Test
        void 요청한_미디어를_그대로_돌려준다() {
            Long media1 = media(1L, UploadStatus.READY);
            Long media2 = media(1L, UploadStatus.READY);

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(media1, media2), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactlyInAnyOrder(media1, media2);
        }

        @Test
        void 다른_방_미디어는_조용히_빠진다() {
            Long inRoom = media(1L, UploadStatus.READY);
            Long otherRoom = media(2L, UploadStatus.READY);

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(inRoom, otherRoom), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(inRoom);
        }

        @Test
        void READY가_아닌_미디어는_대상에서_빠진다() {
            Long ready = media(1L, UploadStatus.READY);
            Long processing = media(1L, UploadStatus.PROCESSING);

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, List.of(ready, processing), null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(ready);
        }

        @Test
        void 요청한_id가_전부_존재하지만_전부_READY가_아니면_404() {
            Long processing = media(1L, UploadStatus.PROCESSING);

            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, List.of(processing), null))
                .isInstanceOf(MediaNotFoundException.class);
        }

        @Test
        void 요청한_id가_전부_존재하지_않으면_404() {
            assertThatThrownBy(() -> downloadTargetResolver.resolve(1L, List.of(999_999L), null))
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
            Long ready = media(1L, UploadStatus.READY);
            Long processing = media(1L, UploadStatus.PROCESSING);
            folderMediaRepository.attachToFolder(folder.getId(), List.of(ready, processing));

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, null, folder.getId());

            assertThat(resolved).extracting(StoredFile::getId).containsExactly(ready);
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
            Long ready1 = media(1L, UploadStatus.READY);
            Long ready2 = media(1L, UploadStatus.READY);
            media(1L, UploadStatus.PROCESSING);
            media(2L, UploadStatus.READY);

            List<StoredFile> resolved = downloadTargetResolver.resolve(1L, null, null);

            assertThat(resolved).extracting(StoredFile::getId).containsExactlyInAnyOrder(ready1, ready2);
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
