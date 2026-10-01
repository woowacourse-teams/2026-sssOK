package com.sssok.domain.room;

import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.RoomStatus;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한을 관리한다.
@Getter
public class Room {

    private final Long id;
    // 읽은 뒤 저장하기까지 다른 요청이 이 방을 바꿨는지 판별하는 값. 저장 전이면 null 이다.
    private final Long version;
    private final RoomCode code;
    private RoomName name;
    private RoomStatus status;
    private RoomExpiration expiration;
    private UploadPolicy uploadPolicy;
    private final Long hostId;
    private final Instant createdAt;
    private Instant deletedAt;

    private Room(
        Long id,
        Long version,
        RoomCode code,
        RoomName name,
        RoomStatus status,
        RoomExpiration expiration,
        UploadPolicy uploadPolicy,
        Long hostId,
        Instant createdAt,
        Instant deletedAt
    ) {
        this.id = id;
        this.version = version;
        this.code = code;
        this.name = name;
        this.status = status;
        this.expiration = expiration;
        this.uploadPolicy = uploadPolicy;
        this.hostId = hostId;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static Room create(RoomCode code, RoomName name, Long hostId, Instant now) {
        return create(code, name, hostId, now, UploadPolicy.ANYONE, RoomExpiration.defaultFrom(now));
    }

    public static Room create(
        RoomCode code,
        RoomName name,
        Long hostId,
        Instant now,
        UploadPolicy uploadPolicy,
        RoomExpiration expiration
    ) {
        return new Room(null, null, code, name, RoomStatus.initial(), expiration, uploadPolicy, hostId, now, null);
    }

    // 저장소에서 불러온 값으로 복원
    public static Room reconstruct(
        Long id,
        Long version,
        RoomCode code,
        RoomName name,
        RoomStatus status,
        RoomExpiration expiration,
        UploadPolicy uploadPolicy,
        Long hostId,
        Instant createdAt,
        Instant deletedAt
    ) {
        return new Room(id, version, code, name, status, expiration, uploadPolicy, hostId, createdAt, deletedAt);
    }

    public boolean isHost(Long memberId) {
        return hostId.equals(memberId);
    }

    public boolean canEnter(Instant now) {
        return statusAt(now).canEnter();
    }

    // 만료 시각이 지나도 저장된 상태는 ACTIVE 로 남아 있어, 응답에 내릴 상태는 시각까지 보고 정한다.
    // 삭제·만료처럼 이미 닫힌 상태는 그대로 둔다.
    public RoomStatus statusAt(Instant now) {
        if (status.canEnter() && expiration.isExpired(now)) {
            return status.toExpired();
        }
        return status;
    }

    public boolean canUpload(Long requester, Instant now) {
        return canEnter(now) && status.canUpload(uploadPolicy, isHost(requester));
    }

    // 만료된 방은 아직 지울 수 있다. 다시 지울 수 없는 건 이미 삭제된 방뿐이다.
    public boolean isDeleted() {
        return status.isDeleted();
    }

    public void updateSettings(Long requester, RoomName newName, RoomExpiration newExpiration, UploadPolicy newUploadPolicy) {
        requireHost(requester);
        this.name = newName;
        this.expiration = newExpiration;
        this.uploadPolicy = newUploadPolicy;
    }

    public void delete(Long requester, Instant now) {
        requireHost(requester);
        this.status = status.toDeleted();
        this.deletedAt = now;
    }

    public void expire() {
        this.status = status.toExpired();
    }

    private void requireHost(Long requester) {
        if (!isHost(requester)) {
            throw new RoomHostRequiredException();
        }
    }
}
