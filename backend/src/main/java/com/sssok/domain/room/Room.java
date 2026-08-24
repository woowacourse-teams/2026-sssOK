package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidPasscodeException;
import com.sssok.domain.room.exception.PasscodeRequiredException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import java.time.Duration;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.RoomStatus;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한, 입장 암호를 관리한다.
@Getter
public class Room {

    private static final Duration RETENTION_AFTER_DELETE = Duration.ofDays(7);

    private final Long id;
    private final RoomCode code;
    private RoomName name;
    private RoomStatus status;
    private RoomExpiration expiration;
    private UploadPolicy uploadPolicy;
    private final EntryPassword entryPassword;
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
        EntryPassword entryPassword,
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
        this.entryPassword = entryPassword;
        this.hostId = hostId;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static Room create(RoomCode code, RoomName name, Long hostId, Instant now) {
        return create(code, name, hostId, null, now);
    }

    // 신규 생성. 만료 시간은 생성 시점에 24시간 뒤로 고정하고, 업로드 권한은 ANYONE으로 시작한다.
    public static Room create(RoomCode code, RoomName name, Long hostId, EntryPassword entryPassword, Instant now) {
        return new Room(null, code, name, RoomStatus.initial(),
            RoomExpiration.defaultFrom(now), UploadPolicy.ANYONE, entryPassword, hostId, now, null);
    }

    // 저장소에서 불러온 값으로 복원
    public static Room reconstruct(
        Long id,
        RoomCode code,
        RoomName name,
        RoomStatus status,
        RoomExpiration expiration,
        UploadPolicy uploadPolicy,
        EntryPassword entryPassword,
        Long hostId,
        Instant createdAt,
        Instant deletedAt
    ) {
        return new Room(id, code, name, status, expiration, uploadPolicy, entryPassword, hostId, createdAt, deletedAt);
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

    public boolean requiresPasscode() {
        return entryPassword != null;
    }

    public void verifyEntry(String rawPasscode) {
        if (!requiresPasscode()) {
            return;
        }
        if (rawPasscode == null || rawPasscode.isBlank()) {
            throw new PasscodeRequiredException();
        }
        if (!entryPassword.matches(rawPasscode, code)) {
            throw new InvalidPasscodeException();
        }
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
