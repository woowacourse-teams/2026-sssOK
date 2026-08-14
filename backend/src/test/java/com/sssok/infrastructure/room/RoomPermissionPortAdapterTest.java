package com.sssok.infrastructure.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomPermissionPortAdapterTest {

    private static final Long ROOM_ID = 1L;
    private static final Long HOST_ID = 100L;
    private static final Long GUEST_ID = 200L;
    private static final Long MISSING_ROOM_ID = 999L;

    private FakeRoomRepository roomRepository;
    private RoomPermissionPortAdapter adapter;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        adapter = new RoomPermissionPortAdapter(roomRepository);
    }

    private Room roomWith(UploadPolicy uploadPolicy, Instant expiresAt) {
        return Room.reconstruct(
            ROOM_ID,
            new RoomCode("A3F9K2M7"),
            new RoomName("우테코 데모데이"),
            RoomStatus.initial(),
            new RoomExpiration(expiresAt),
            uploadPolicy,
            HOST_ID,
            Instant.now().minusSeconds(60),
            null
        );
    }

    private void givenRoom(Room room) {
        roomRepository.save(room);
    }

    private Instant farFuture() {
        return Instant.now().plusSeconds(3600);
    }

    private Instant past() {
        return Instant.now().minusSeconds(1);
    }

    @Test
    void 방장이면_isHost_가_참이다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, farFuture()));

        assertThat(adapter.isHost(ROOM_ID, HOST_ID)).isTrue();
        assertThat(adapter.isHost(ROOM_ID, GUEST_ID)).isFalse();
    }

    @Test
    void ANYONE_방에서는_누구나_업로드할_수_있다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, farFuture()));

        assertThat(adapter.canUpload(ROOM_ID, GUEST_ID)).isTrue();
    }

    @Test
    void HOST_ONLY_방에서는_방장만_업로드할_수_있다() {
        givenRoom(roomWith(UploadPolicy.HOST_ONLY, farFuture()));

        assertThat(adapter.canUpload(ROOM_ID, HOST_ID)).isTrue();
        assertThat(adapter.canUpload(ROOM_ID, GUEST_ID)).isFalse();
    }

    @Test
    void 만료된_방에는_방장도_업로드할_수_없다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, past()));

        assertThat(adapter.canUpload(ROOM_ID, HOST_ID)).isFalse();
    }

    @Test
    void 살아있는_방은_사용_가능하다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, farFuture()));

        assertThat(adapter.isRoomUsable(ROOM_ID)).isTrue();
    }

    @Test
    void 만료된_방은_사용할_수_없다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, past()));

        assertThat(adapter.isRoomUsable(ROOM_ID)).isFalse();
    }

    @Test
    void 방_코드를_조회할_수_있다() {
        givenRoom(roomWith(UploadPolicy.ANYONE, farFuture()));

        assertThat(adapter.getRoomCode(ROOM_ID)).isEqualTo("A3F9K2M7");
    }

    @Test
    void 없는_방은_모든_판별이_거짓이다() {
        assertThat(adapter.isHost(MISSING_ROOM_ID, HOST_ID)).isFalse();
        assertThat(adapter.canUpload(MISSING_ROOM_ID, HOST_ID)).isFalse();
        assertThat(adapter.isRoomUsable(MISSING_ROOM_ID)).isFalse();
    }

    @Test
    void 없는_방의_코드를_조회하면_예외() {
        assertThatThrownBy(() -> adapter.getRoomCode(MISSING_ROOM_ID))
            .isInstanceOf(RoomNotFoundException.class);
    }

    // DB 없이 검증하기 위한 최소 구현
    private static class FakeRoomRepository implements RoomRepository {

        private final Map<Long, Room> rooms = new HashMap<>();

        @Override
        public Room save(Room room) {
            rooms.put(room.getId(), room);
            return room;
        }

        @Override
        public Optional<Room> findByCode(RoomCode code) {
            return rooms.values().stream()
                .filter(room -> room.getCode().equals(code))
                .findFirst();
        }

        @Override
        public Optional<Room> findById(Long id) {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}