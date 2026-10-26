package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.RoomMember;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
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
    private final MemberRepository memberRepository;

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
        return jpaRepository.insertIfAbsent(roomId, memberId, joinedAt, null) > 0;
    }

    @Override
    public boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt, String nickname) {
        return jpaRepository.insertIfAbsent(roomId, memberId, joinedAt, nickname) > 0;
    }

    @Override
    public List<RoomMember> findByRoomsAndMembers(Collection<Long> roomIds,
                                                          Collection<Long> memberIds) {
        return jpaRepository.findByRoomIdInAndMemberIdIn(roomIds, memberIds).stream().map(this::toDomain).toList();
    }

    private RoomMemberJpaEntity toEntity(RoomMember roomMember) {
        return new RoomMemberJpaEntity(
            roomMember.getId(),
            roomMember.getRoomId(),
            roomMember.getMemberId(),
            roomMember.getJoinedAt(),
            roomMember.getNickname() != null ? roomMember.getNickname() : memberRepository.findById(roomMember.getMemberId())
                .map(member -> member.getDisplayName().value()).orElse(null)
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
