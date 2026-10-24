package com.sssok.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.UpdateRoomCommand;
import com.sssok.application.room.UpdateRoomService;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import com.sssok.renewal.outgoing.database.member.MemberJpaEntity;
import com.sssok.renewal.outgoing.database.member.MemberJpaRepository;
import com.sssok.infrastructure.persistence.room.RoomJpaEntity;
import com.sssok.infrastructure.persistence.room.RoomJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.support.H2IntegrationTest;

@H2IntegrationTest
class BaseEntityTest {

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    UpdateRoomService updateRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomJpaRepository roomJpaRepository;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    private Long hostId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
    }

    @Test
    void 저장하면_생성_시각과_수정_시각이_채워진다() {
        MemberJpaEntity member = memberJpaRepository.findById(hostId).orElseThrow();

        assertThat(member.getCreatedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    void 수정하면_수정_시각만_바뀌고_생성_시각은_그대로다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();
        RoomJpaEntity before = roomJpaRepository.findById(room.getId()).orElseThrow();
        Instant createdAt = before.getCreatedAt();
        Instant updatedAt = before.getUpdatedAt();

        updateRoomService.update(room.getId(), hostId, new UpdateRoomCommand("2차 회식", null));

        RoomJpaEntity after = roomJpaRepository.findById(room.getId()).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }

    @Test
    void 어댑터가_넘긴_생성_시각을_덮어쓰지_않는다() {
        Instant past = Instant.now().minus(Duration.ofDays(3));
        Room saved = roomRepository.save(오래전에_만든_방(past));

        assertThat(roomJpaRepository.findById(saved.getId()).orElseThrow().getCreatedAt())
            .isCloseTo(past, within(1, ChronoUnit.SECONDS));
    }

    private Room 오래전에_만든_방(Instant createdAt) {
        return Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            RoomStatus.initial(),
            UploadPolicy.ANYONE,
            hostId,
            createdAt,
            null
        );
    }
}
