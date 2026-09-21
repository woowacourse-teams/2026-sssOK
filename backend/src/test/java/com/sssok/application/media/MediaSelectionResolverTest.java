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
            ROOM_ID, MediaSelection.include(List.of(1L, 2L, 1L)));

        assertThat(result.files()).containsExactly(first);
        assertThat(result.notFoundIds()).containsExactly(2L);
    }

    @Test
    void exclude는_저장소에서_제외_조건을_적용한다() {
        StoredFile first = file(1L);
        given(fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, List.of(7L)))
            .willReturn(List.of(first));

        ResolvedMediaSelection result = resolver.resolve(
            ROOM_ID, MediaSelection.exclude(List.of(7L, 7L)));

        assertThat(result.files()).containsExactly(first);
        assertThat(result.notFoundIds()).isEmpty();
        verify(fileRepository).findAllByRoomIdAndIdNotIn(ROOM_ID, List.of(7L));
    }

    @Test
    void exclude의_빈_ID는_방_전체를_선택한다() {
        resolver.resolve(ROOM_ID, MediaSelection.exclude(List.of()));

        verify(fileRepository).findAllByRoomIdAndIdNotIn(ROOM_ID, List.of());
    }

    @Test
    void null이나_0_이하_ID를_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, MediaSelection.include(Arrays.asList(1L, null))))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, MediaSelection.include(List.of(0L))))
            .isInstanceOf(InvalidMediaSelectionException.class);
    }

    @Test
    void mode나_ids_누락을_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(ROOM_ID, null))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, new MediaSelection(null, List.of())))
            .isInstanceOf(InvalidMediaSelectionException.class);
        assertThatThrownBy(() -> resolver.resolve(
            ROOM_ID, new MediaSelection(MediaSelectionMode.INCLUDE, null)))
            .isInstanceOf(InvalidMediaSelectionException.class);
    }

    private StoredFile file(Long id) {
        Instant now = Instant.now();
        return StoredFile.reconstruct(id, ROOM_ID, 1L, "사진.jpg", MediaType.JPEG,
            new FileSize(1024), new StorageKey("rooms/1/" + id + ".jpg"), null,
            UploadStatus.READY, now, now, 0, null, null, null, null, null, null);
    }
}
