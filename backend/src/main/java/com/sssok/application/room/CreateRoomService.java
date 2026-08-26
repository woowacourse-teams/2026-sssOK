package com.sssok.application.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 생성
@Service
@RequiredArgsConstructor
public class CreateRoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomDetailReader roomDetailReader;

    private final RandomGenerator randomGenerator = new SecureRandom();

    @Transactional
    public RoomDetail create(Long hostId, String name, String uploadPolicy, Integer expiryHours) {
        Instant now = Instant.now();
        Room room = Room.create(
            RoomCode.generate(randomGenerator),
            new RoomName(name),
            hostId,
            now,
            resolveUploadPolicy(uploadPolicy),
            resolveExpiration(now, expiryHours)
        );
        Room saved = roomRepository.save(room);

        // 방장은 자기 방에 별도로 입장 API를 호출하지 않아도 참여자로 등록돼 있어야 자연스럽다.
        // 방금 만든 roomId라 동시 입장 경쟁이 있을 수 없으므로, joinIfAbsent(ON CONFLICT 원자적 삽입)까지는 필요 없다.
        roomMemberRepository.save(RoomMember.join(saved.getId(), hostId, now));

        return roomDetailReader.read(saved, hostId);
    }

    private UploadPolicy resolveUploadPolicy(String uploadPolicy) {
        return uploadPolicy == null ? UploadPolicy.ANYONE : UploadPolicy.from(uploadPolicy);
    }

    private RoomExpiration resolveExpiration(Instant now, Integer expiryHours) {
        return expiryHours == null ? RoomExpiration.defaultFrom(now) : RoomExpiration.from(now, expiryHours);
    }
}
