package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.application.media.exception.InvalidMediaDeleteParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.TooManyMediaException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteMediaServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long REQUESTER_ID = 2L;

    @Mock
    FileRepository fileRepository;

    @Mock
    RoomPermissionPort roomPermissionPort;

    @Mock
    MediaDeleter mediaDeleter;

    DeleteMediaService service;

    @BeforeEach
    void setUp() {
        service = new DeleteMediaService(fileRepository, roomPermissionPort, mediaDeleter);
    }

    @Test
    void 올린_본인은_단건_삭제할_수_있다() {
        StoredFile file = file(1L, ROOM_ID, REQUESTER_ID);
        given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));
        given(roomPermissionPort.isHost(ROOM_ID, REQUESTER_ID)).willReturn(false);

        assertThat(service.deleteOne(ROOM_ID, file.getId(), REQUESTER_ID)).isEqualTo(file.getId());
        verify(mediaDeleter).delete(ROOM_ID, List.of(file));
    }

    @Test
    void 방장은_다른_사람의_미디어도_삭제할_수_있다() {
        StoredFile file = file(1L, ROOM_ID, 99L);
        given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));
        given(roomPermissionPort.isHost(ROOM_ID, REQUESTER_ID)).willReturn(true);

        service.deleteOne(ROOM_ID, file.getId(), REQUESTER_ID);

        verify(mediaDeleter).delete(ROOM_ID, List.of(file));
    }

    @Test
    void 다른_참여자는_삭제할_수_없다() {
        StoredFile file = file(1L, ROOM_ID, 99L);
        given(fileRepository.findById(file.getId())).willReturn(Optional.of(file));

        assertThatThrownBy(() -> service.deleteOne(ROOM_ID, file.getId(), REQUESTER_ID))
            .isInstanceOf(MediaForbiddenException.class);
        verify(mediaDeleter, never()).delete(ROOM_ID, List.of(file));
    }

    @Test
    void 없거나_다른_방의_미디어는_단건_삭제에서_404() {
        StoredFile otherRoomFile = file(1L, 20L, REQUESTER_ID);
        given(fileRepository.findById(1L)).willReturn(Optional.of(otherRoomFile));

        assertThatThrownBy(() -> service.deleteOne(ROOM_ID, 1L, REQUESTER_ID))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다건_삭제는_유효한_항목만_삭제하고_나머지를_구분한다() {
        StoredFile first = file(1L, ROOM_ID, REQUESTER_ID);
        StoredFile second = file(2L, ROOM_ID, REQUESTER_ID);
        StoredFile otherRoom = file(3L, 20L, REQUESTER_ID);
        given(fileRepository.findAllByIdIn(List.of(1L, 999L, 2L, 3L)))
            .willReturn(List.of(second, otherRoom, first));

        DeleteMediaResult result = service.deleteAll(
            ROOM_ID, List.of(1L, 999L, 2L, 1L, 3L), REQUESTER_ID);

        assertThat(result.deletedMediaIds()).containsExactly(1L, 2L);
        assertThat(result.notFoundMediaIds()).containsExactly(999L, 3L);
        assertThat(result.deletedCount()).isEqualTo(2);
        verify(mediaDeleter).delete(ROOM_ID, List.of(first, second));
    }

    @Test
    void 다건에_남의_미디어가_섞이면_전체를_거부한다() {
        StoredFile mine = file(1L, ROOM_ID, REQUESTER_ID);
        StoredFile others = file(2L, ROOM_ID, 99L);
        given(fileRepository.findAllByIdIn(List.of(1L, 2L))).willReturn(List.of(mine, others));

        assertThatThrownBy(() -> service.deleteAll(ROOM_ID, List.of(1L, 2L), REQUESTER_ID))
            .isInstanceOf(MediaForbiddenException.class);
        verify(mediaDeleter, never()).delete(ROOM_ID, List.of(mine, others));
    }

    @Test
    void 빈_목록은_거부한다() {
        assertThatThrownBy(() -> service.deleteAll(ROOM_ID, List.of(), REQUESTER_ID))
            .isInstanceOf(InvalidMediaDeleteParamException.class);
    }

    @Test
    void null_ID가_섞인_목록은_거부한다() {
        assertThatThrownBy(() -> service.deleteAll(
            ROOM_ID, java.util.Arrays.asList(1L, null), REQUESTER_ID))
            .isInstanceOf(InvalidMediaDeleteParamException.class);
    }

    @Test
    void 최대_개수를_넘으면_거부한다() {
        List<Long> ids = LongStream.rangeClosed(1, 501).boxed().toList();

        assertThatThrownBy(() -> service.deleteAll(ROOM_ID, ids, REQUESTER_ID))
            .isInstanceOf(TooManyMediaException.class);
    }

    private StoredFile file(Long id, Long roomId, Long uploaderId) {
        Instant now = Instant.now();
        return StoredFile.reconstruct(id, roomId, uploaderId, "사진.jpg", MediaType.JPEG,
            new FileSize(1024), new StorageKey("rooms/%d/%d.jpg".formatted(roomId, id)), null,
            UploadStatus.READY, now, now, 0, null, null, null, null, null);
    }
}
