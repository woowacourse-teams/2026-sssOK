package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

// 방의 작업 점유 상태와 그 변화 버전. 접수와 종료처럼 점유가 바뀔 때만 version 이 올라간다.
// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
@Table(name = "organize_room_state")
@Check(name = "ck_organize_room_state_version", constraints = "version > 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeRoomStateJpaEntity extends BaseEntity {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "busy", nullable = false)
    private boolean busy;

    public OrganizeRoomStateJpaEntity(Long roomId, long version, boolean busy, Instant createdAt) {
        super(createdAt);
        this.roomId = roomId;
        this.version = version;
        this.busy = busy;
    }
}
