package com.sssok.domain.room;

import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Duration;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.RoomStatus;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한을 관리한다.
@Getter
public class Room {

    private static final Duration RETENTION_AFTER_DELETE = Duration.ofDays(30);

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

    // 신규 생성. 만료 시간은 생성 시점에 24시간 뒤로 고정하고, 업로드 권한은 ANYONE으로 시작한다.
    // (방장이 이후 설정 변경으로 만료 시간을 늘릴 수 있는 여지는 updateSettings 에 남겨둔다.)
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

    // 상태(ACTIVE)뿐 아니라 실제 만료 시각도 함께 본다 — 만료 배치가 아직
    // 상태를 EXPIRED로 못 바꿨어도, 만료 시각이 지났으면 즉시 입장을 막는다.
    public boolean canEnter(Instant now) {
        return status.canEnter() && !expiration.isExpired(now);
    }

    public boolean canUpload(Long requester, Instant now) {
        return canEnter(now) && status.canUpload(uploadPolicy, isHost(requester));
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

    // 만료 배치가 호출 — 만료 시각이 지난 ACTIVE 방을 EXPIRED로 전이시킨다.
    public void expire() {
        this.status = status.toExpired();
    }

    // 정리 배치가 호출 — 30일 지난 DELETED 방만 영구 삭제 대상.
    public void purge() {
        this.status = status.toPurged();
    }

    public boolean isPurgeable(Instant now) {
        return deletedAt != null && !now.isBefore(deletedAt.plus(RETENTION_AFTER_DELETE));
    }

    private void requireHost(Long requester) {
        if (!isHost(requester)) {
            throw new RoomHostRequiredException();
        }
    }
}
