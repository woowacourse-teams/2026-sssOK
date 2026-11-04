package com.sssok.infrastructure.realtime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomEventJpaRepository extends JpaRepository<RoomEventJpaEntity, Long> {

    List<RoomEventJpaEntity> findByRoomIdAndIdGreaterThanOrderById(Long roomId, Long id);

    // 재연결 때 놓친 이벤트. 방 전체 이벤트와 이 회원 전용 이벤트만 돌려준다.
    @Query("""
        select e from RoomEventJpaEntity e
        where e.roomId = :roomId and e.id > :lastEventId
          and (e.recipientMemberId is null or e.recipientMemberId = :memberId)
        order by e.id
        """)
    List<RoomEventJpaEntity> findVisibleAfter(@Param("roomId") Long roomId, @Param("lastEventId") Long lastEventId,
                                              @Param("memberId") Long memberId);
}
