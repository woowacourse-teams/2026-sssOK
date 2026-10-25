package com.sssok.application.port.out;

import com.sssok.application.room.RoomMembershipSummary;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 방 참여자 영속화 출력
public interface RoomMemberRepository {

    RoomMember save(RoomMember roomMember);

    List<RoomMembershipSummary> findRoomsByMemberId(Long memberId);

    // 실제 삭제한 참여 기록 ID. 다른 요청이 먼저 삭제했거나 이미 나갔으면 비어 있다.
    Optional<Long> deleteMembership(Long roomId, Long memberId);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);

    // 참여 기록이 없을 때만 넣고, 실제로 넣었으면 true.
    // 동시에 같은 사람의 입장 요청이 여러 개 들어와도 단 하나만 true를 받는다.
    boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt);
}
