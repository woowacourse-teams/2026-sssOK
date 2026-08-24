package com.sssok.domain.room;

import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Duration;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.RoomStatus;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한을 관리한다.
@Getter
public class Room {

    private static final Duration RETENTION_AFTER_DELETE = Duration.ofDays(7);

    private final Long id;
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
        return new Room(null, code, name, RoomStatus.initial(),
            RoomExpiration.defaultFrom(now), UploadPolicy.ANYONE, hostId, now, null);
    }

    // 저장소에서 불러온 값으로 복원
    public static Room reconstruct(
        Long id,
        RoomCode code,
        RoomName name,
        RoomStatus status,
        RoomExpiration expiration,
        UploadPolicy uploadPolicy,
        Long hostId,
        Instant createdAt,
        Instant deletedAt
    ) {
        return new Room(id, code, name, status, expiration, uploadPolicy, hostId, createdAt, deletedAt);
    }

    public boolean isHost(Long memberId) {
        return hostId.equals(memberId);
    }

    public boolean canEnter(Instant now) {
        return status.canEnter() && !expiration.isExpired(now);
    }

    public boolean canUpload(Long requester, Instant now) {
        return canEnter(now) && status.canUpload(uploadPolicy, isHost(requester));
    }

    // 만료된 방은 아직 지울 수 있다. 지울 게 남지 않은 건 이미 삭제·정리된 방뿐이다.
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

    public void purge() {
        this.status = status.toPurged();
    }

    public Instant purgeAt() {
        if (deletedAt == null) {
            return null;
        }
        return deletedAt.plus(RETENTION_AFTER_DELETE);
    }

    public boolean isPurgeable(Instant now) {
        Instant purgeAt = purgeAt();
        return purgeAt != null && !now.isBefore(purgeAt);
    }

    private void requireHost(Long requester) {
        if (!isHost(requester)) {
            throw new RoomHostRequiredException();
        }
    }
}
