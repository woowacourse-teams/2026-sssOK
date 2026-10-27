package com.sssok.application.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.RoomMember;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomNicknameReader {
    private final RoomMemberRepository roomMemberRepository;
    private final MemberRepository memberRepository;

    public record Participant(Long roomId, Long memberId) {}

    public String read(Long roomId, Long memberId) {
        return readAll(List.of(roomId), List.of(memberId)).get(new Participant(roomId, memberId));
    }

    public Map<Participant, String> readAll(Collection<Long> roomIds, Collection<Long> memberIds) {
        return readRoomNames(roomIds, memberIds, loadMembers(memberIds));
    }

    public Map<Long, String> readUploaderNames(List<StoredFile> files) {
        List<Long> roomIds = files.stream().map(StoredFile::getRoomId).distinct().toList();
        List<Long> memberIds = files.stream().map(StoredFile::getUploaderId).distinct().toList();
        Map<Long, Member> members = loadMembers(memberIds);
        Map<Participant, String> roomNames = readRoomNames(roomIds, memberIds, members);
        Map<Long, String> names = new HashMap<>();
        for (StoredFile file : files) {
            Member member = members.get(file.getUploaderId());
            String nickname = file.getUploaderNickname();
            if (member != null && member.getStatus() == MemberStatus.WITHDRAWN) {
                nickname = member.getDisplayName().value();
            } else if (nickname == null) {
                nickname = roomNames.get(new Participant(file.getRoomId(), file.getUploaderId()));
            }
            names.put(file.getId(), nickname);
        }
        return names;
    }

    private Map<Long, Member> loadMembers(Collection<Long> memberIds) {
        Map<Long, Member> members = new HashMap<>();
        for (Member member : memberRepository.findAllByIdIn(memberIds)) {
            members.put(member.getId(), member);
        }
        return members;
    }

    private Map<Participant, String> readRoomNames(Collection<Long> roomIds, Collection<Long> memberIds,
                                                Map<Long, Member> members) {
        Map<Participant, String> names = new HashMap<>();
        for (Member member : members.values()) {
            for (Long roomId : roomIds) {
                names.put(new Participant(roomId, member.getId()), member.getDisplayName().value());
            }
        }
        for (RoomMember participant : roomMemberRepository.findByRoomsAndMembers(roomIds, memberIds)) {
            Member member = members.get(participant.getMemberId());
            if (member != null && member.getStatus() != MemberStatus.WITHDRAWN && participant.getNickname() != null) {
                names.put(new Participant(participant.getRoomId(), participant.getMemberId()), participant.getNickname());
            }
        }
        return names;
    }
}
