package com.sssok.application.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.domain.room.RoomMember;
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
        Map<Participant, String> names = new HashMap<>();
        Map<Long, Member> members = new HashMap<>();
        for (Member member : memberRepository.findAllByIdIn(memberIds)) {
            members.put(member.getId(), member);
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
