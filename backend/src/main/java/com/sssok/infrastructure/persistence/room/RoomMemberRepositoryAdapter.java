package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.room.RoomMembershipSummary;
import com.sssok.domain.room.RoomMember;
import com.sssok.infrastructure.persistence.room.RoomMemberJpaRepository.RoomMembershipSummaryProjection;
import com.sssok.renewal.domain.member.Nickname;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMemberRepositoryAdapter implements RoomMemberRepository {

    private final RoomMemberJpaRepository jpaRepository;

    @Override
    public List<RoomMembershipSummary> findRoomsByMemberId(Long memberId) {
        return jpaRepository.findRoomsByMemberId(memberId).stream()
            .map(this::toMembershipSummary)
            .toList();
    }

    @Override
    public Optional<Long> deleteMembership(Long roomId, Long memberId) {
        // 조회했던 참여 기록만 지운다. 그 사이 재참여한 새 기록에는 영향을 주지 않는다.
        return jpaRepository.findMembershipId(roomId, memberId)
            .filter(id -> jpaRepository.deleteMembershipById(id) > 0);
    }

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
    public boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt, Nickname nickname) {
        return jpaRepository.insertIfAbsent(roomId, memberId, joinedAt, nickname.value()) > 0;
    }

    @Override
    public List<RoomMember> findByRoomsAndMembers(Collection<Long> roomIds,
                                                          Collection<Long> memberIds) {
        return jpaRepository.findByRoomIdInAndMemberIdIn(roomIds, memberIds).stream().map(this::toDomain).toList();
    }

    private RoomMembershipSummary toMembershipSummary(RoomMembershipSummaryProjection projection) {
        return new RoomMembershipSummary(
            projection.getRoomId(),
            projection.getCode(),
            projection.getName(),
            projection.getIsHost(),
            projection.getMediaCount(),
            projection.getMemberCount(),
            projection.getThumbnailKey()
        );
    }

    private RoomMemberJpaEntity toEntity(RoomMember roomMember) {
        return new RoomMemberJpaEntity(
            roomMember.getId(),
            roomMember.getRoomId(),
            roomMember.getMemberId(),
            roomMember.getJoinedAt(),
            roomMember.getNickname()
        );
    }

    private RoomMember toDomain(RoomMemberJpaEntity entity) {
        return RoomMember.reconstruct(
            entity.getId(),
            entity.getRoomId(),
            entity.getMemberId(),
            entity.getJoinedAt(),
            entity.getNickname()
        );
    }
}
