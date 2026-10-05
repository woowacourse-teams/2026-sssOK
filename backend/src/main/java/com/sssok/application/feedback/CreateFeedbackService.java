package com.sssok.application.feedback;

import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.feedback.FrontendVersion;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.UserAgent;
import com.sssok.domain.member.Member;
import com.sssok.domain.room.Room;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 의견 등록. 방 존재/만료/입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class CreateFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Feedback create(
        Long roomId,
        Long memberId,
        String content,
        String rawUserAgent,
        String rawFrontendVersion
    ) {
        FeedbackContent feedbackContent = new FeedbackContent(content);

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        String nickname = memberRepository.findById(memberId)
            .map(Member::getDisplayName)
            .map(displayName -> displayName.value())
            .orElse(null);

        Feedback saved = feedbackRepository.save(Feedback.write(
            feedbackContent,
            roomId,
            room.getName().value(),
            memberId,
            nickname,
            UserAgent.from(rawUserAgent),
            FrontendVersion.from(rawFrontendVersion),
            Instant.now()
        ));
        eventPublisher.publishEvent(new FeedbackCreatedEvent(saved));
        return saved;
    }
}
