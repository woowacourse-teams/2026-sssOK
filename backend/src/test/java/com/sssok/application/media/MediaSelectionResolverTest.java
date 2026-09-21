package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sssok.application.media.exception.InvalidMediaSelectionException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaSelectionResolverTest {

    private static final Long ROOM_ID = 1L;

    @Mock
    FileRepository fileRepository;

    MediaSelectionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MediaSelectionResolver(fileRepository);
    }

    @Test
    void include는_중복을_제거하고_현재_방의_ID만_선택한다() {
        StoredFile first = file(1L);
        given(fileRepository.findAllByRoomIdAndIdIn(ROOM_ID, List.of(1L, 2L)))
            .willReturn(List.of(first));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.include(List.of(1L, 2L, 1L)), null, MediaUploaderFilter.ALL);

        assertThat(result.files()).containsExactly(first);
        assertThat(result.notFoundIds()).containsExactly(2L);
    }

    @Test
    void exclude는_저장소에서_제외_조건을_적용한다() {
        StoredFile first = file(1L);
        given(fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, List.of(7L)))
            .willReturn(List.of(first));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.exclude(List.of(7L, 7L)), null, MediaUploaderFilter.ALL);

        assertThat(result.files()).containsExactly(first);
        assertThat(result.notFoundIds()).isEmpty();
        verify(fileRepository).findAllByRoomIdAndIdNotIn(ROOM_ID, List.of(7L));
    }

    @Test
    void exclude의_빈_ID는_방_전체를_선택한다() {
        resolver.resolve(ROOM_ID, MediaSelection.exclude(List.of()), null, MediaUploaderFilter.ALL);

        verify(fileRepository).findAllByRoomIdAndIdNotIn(ROOM_ID, List.of());
    }

    @Test
    void ME는_요청자가_올린_미디어만_선택한다() {
        StoredFile mine = file(1L, 7L);
        StoredFile others = file(2L, 8L);
        given(fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, List.of()))
            .willReturn(List.of(mine, others));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.exclude(List.of()), 7L, MediaUploaderFilter.ME);

        assertThat(result.files()).containsExactly(mine);
    }

    @Test
    void OTHERS는_요청자가_올리지_않은_미디어만_선택한다() {
        StoredFile mine = file(1L, 7L);
        StoredFile others = file(2L, 8L);
        given(fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, List.of()))
            .willReturn(List.of(mine, others));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.exclude(List.of()), 7L, MediaUploaderFilter.OTHERS);

        assertThat(result.files()).containsExactly(others);
    }

    @Test
    void uploader를_생략하면_ALL로_처리한다() {
        StoredFile mine = file(1L, 7L);
        StoredFile others = file(2L, 8L);
        given(fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, List.of()))
            .willReturn(List.of(mine, others));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.exclude(List.of()), 7L, null);

        assertThat(result.files()).containsExactly(mine, others);
    }

    @Test
    void null이나_0_이하_ID를_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, MediaSelection.include(Arrays.asList(1L, null)), null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, MediaSelection.include(List.of(0L)), null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaSelectionException.class);
    }

    @Test
    void mode나_ids_누락을_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(ROOM_ID, null, null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, new MediaSelection(null, List.of()), null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, new MediaSelection(MediaSelectionMode.INCLUDE, null), null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaSelectionException.class);
    }

    private StoredFile file(Long id) {
        return file(id, 1L);
    }

    private StoredFile file(Long id, Long uploaderId) {
        Instant now = Instant.now();
        return StoredFile.reconstruct(id, ROOM_ID, uploaderId, "사진.jpg", MediaType.JPEG,
            new FileSize(1024), new StorageKey("rooms/1/" + id + ".jpg"), null,
            UploadStatus.READY, now, now, 0, null, null, null, null, null, null);
    }

    // exclude 는 방 전체에서 빼는 경로라 위 테스트들이 덮지만, include 는 경로가 따로다.
    @Test
    void include와_ME를_함께_적용한다() {
        StoredFile mine = file(1L, 7L);
        StoredFile others = file(2L, 8L);
        given(fileRepository.findAllByRoomIdAndIdIn(ROOM_ID, List.of(1L, 2L)))
            .willReturn(List.of(mine, others));

        ResolvedMediaSelection resolved = resolver.resolve(
            ROOM_ID, MediaSelection.include(List.of(1L, 2L)), 7L, MediaUploaderFilter.ME);

        assertThat(resolved.files()).containsExactly(mine);
    }

    // 고른 id 중 업로더 조건에 걸러진 것은 "없는 id" 가 아니다 — 지웠다고 잘못 보고하면 안 된다.
    @Test
    void 업로더_조건에_걸러진_id는_notFound가_아니다() {
        given(fileRepository.findAllByRoomIdAndIdIn(ROOM_ID, List.of(1L, 2L)))
            .willReturn(List.of(file(1L, 7L), file(2L, 8L)));

        ResolvedMediaSelection resolved = resolver.resolve(
            ROOM_ID, MediaSelection.include(List.of(1L, 2L)), 7L, MediaUploaderFilter.ME);

        assertThat(resolved.notFoundIds()).isEmpty();
    }

}
