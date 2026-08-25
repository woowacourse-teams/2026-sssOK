package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMemberRepositoryAdapter implements RoomMemberRepository {

    private final RoomMemberJpaRepository jpaRepository;

    @Override
    public RoomMember save(RoomMember roomMember) {
        RoomMemberJpaEntity saved = jpaRepository.save(toEntity(roomMember));
        return toDomain(saved);
    }

    @Override
    public Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId) {
        return jpaRepository.findByRoomIdAndMemberId(roomId, memberId).map(this::toDomain);
    }

    @Override
    public boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt) {
        return jpaRepository.insertIfAbsent(roomId, memberId, joinedAt) > 0;
    }

    @Override
    public void deleteAllByRoomId(Long roomId) {
        jpaRepository.deleteAllByRoomId(roomId);
    }

    private RoomMemberJpaEntity toEntity(RoomMember roomMember) {
        return new RoomMemberJpaEntity(
            roomMember.getId(),
            roomMember.getRoomId(),
            roomMember.getMemberId(),
            roomMember.getJoinedAt()
        );
    }

    private RoomMember toDomain(RoomMemberJpaEntity entity) {
        return RoomMember.reconstruct(
            entity.getId(),
            entity.getRoomId(),
            entity.getMemberId(),
            entity.getJoinedAt()
        );
    }
}
