package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sssok.application.media.exception.InvalidMediaIdsException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaIdsResolverTest {

    private static final Long ROOM_ID = 1L;

    @Mock
    FileRepository fileRepository;

    @InjectMocks
    MediaIdsResolver resolver;

    @Test
    void 중복을_제거하고_요청_ID_순서대로_반환한다() {
        StoredFile first = file(1L);
        StoredFile second = file(2L);
        given(fileRepository.findAllByRoomIdAndIdIn(ROOM_ID, List.of(2L, 1L)))
            .willReturn(List.of(first, second));

        ResolvedMediaIds result = resolver.resolve(ROOM_ID, List.of(2L, 1L, 2L));

        assertThat(result.files()).extracting(StoredFile::getId).containsExactly(2L, 1L);
        assertThat(result.notFoundIds()).isEmpty();
    }

    @Test
    void 방에서_찾지_못한_ID를_구분한다() {
        StoredFile first = file(1L);
        given(fileRepository.findAllByRoomIdAndIdIn(ROOM_ID, List.of(1L, 999L)))
            .willReturn(List.of(first));

        ResolvedMediaIds result = resolver.resolve(ROOM_ID, List.of(1L, 999L));

        assertThat(result.files()).containsExactly(first);
        assertThat(result.notFoundIds()).containsExactly(999L);
    }

    @Test
    void null_목록이나_유효하지_않은_ID를_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(ROOM_ID, null))
            .isInstanceOf(InvalidMediaIdsException.class);
        assertThatThrownBy(() -> resolver.resolve(ROOM_ID, Arrays.asList(1L, null)))
            .isInstanceOf(InvalidMediaIdsException.class);
        assertThatThrownBy(() -> resolver.resolve(ROOM_ID, List.of(0L)))
            .isInstanceOf(InvalidMediaIdsException.class);
    }

    private StoredFile file(Long id) {
        Instant now = Instant.now();
        return StoredFile.reconstruct(id, ROOM_ID, 1L, "사진.jpg", MediaType.JPEG,
            new FileSize(1024), new StorageKey("rooms/1/%d.jpg".formatted(id)), null,
            UploadStatus.READY, now, now, 0, null, null, null, null, null, null);
    }
}
