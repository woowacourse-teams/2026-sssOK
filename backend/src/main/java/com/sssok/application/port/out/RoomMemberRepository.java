package com.sssok.application.port.out;

import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 방 참여자 영속화 출력
public interface RoomMemberRepository {

    RoomMember save(RoomMember roomMember);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);

    // 참여 기록이 없을 때만 넣고, 실제로 넣었으면 true.
    // 동시에 같은 사람의 입장 요청이 여러 개 들어와도 단 하나만 true를 받는다.
    boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt);

    void deleteAllByRoomId(Long roomId);

    // 방을 지우기 전에 누구를 함께 지울지 추려두기 위한 조회. 참여 기록이 사라지면 알 수 없다.
    List<Long> findMemberIdsByRoomId(Long roomId);

    // 넘긴 회원 중 아직 어딘가의 방에 남아 있는 사람. 여기 없는 사람은 지워도 잃을 참여가 없다.
    List<Long> findMemberIdsStillInAnyRoom(Collection<Long> memberIds);
}
