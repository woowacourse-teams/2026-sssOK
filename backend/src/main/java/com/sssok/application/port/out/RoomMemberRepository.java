package com.sssok.application.port.out;

import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Optional;

// 방 참여자 영속화 출력
public interface RoomMemberRepository {

    RoomMember save(RoomMember roomMember);

    Optional<RoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);

    // 참여 기록이 없을 때만 넣고, 실제로 넣었으면 true.
    // 동시에 같은 사람의 입장 요청이 여러 개 들어와도 단 하나만 true를 받는다.
    boolean joinIfAbsent(Long roomId, Long memberId, Instant joinedAt);

    // 방을 영구 삭제할 때 참여 기록만 지운다. 회원 계정(member)은 방과 무관하므로 건드리지 않는다.
    void deleteAllByRoomId(Long roomId);
}
