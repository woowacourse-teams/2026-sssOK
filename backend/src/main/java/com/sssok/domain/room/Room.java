package com.sssok.domain.room;

import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.RoomStatus;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한을 관리한다.
@Getter
public class Room {

    private static final RetentionPolicy RETENTION_POLICY = new RetentionPolicy();

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

    // 방이 더는 쓰이지 않게 된 시각. 삭제와 만료 중 먼저 온 쪽이다.
    // 만료된 방을 뒤늦게 지웠다고 보관 기간이 늘어나면, 정리하려던 행동이 수명을 늘리는 셈이 된다.
    public Instant endedAt() {
        Instant expiresAt = expiration.expiresAt();
        if (deletedAt == null || expiresAt.isBefore(deletedAt)) {
            return expiresAt;
        }
        return deletedAt;
    }

    public Instant purgeAt() {
        return RETENTION_POLICY.purgeAt(endedAt());
    }

    public boolean isPurgeable(Instant now) {
        return !now.isBefore(purgeAt());
    }

    private void requireHost(Long requester) {
        if (!isHost(requester)) {
            throw new RoomHostRequiredException();
        }
    }
}
