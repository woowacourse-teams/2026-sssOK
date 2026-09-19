package com.sssok.application.feedback;

import com.sssok.application.feedback.exception.FeedbackRateLimitedException;
import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.feedback.AppVersion;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.UserAgent;
import com.sssok.domain.member.Member;
import com.sssok.domain.room.Room;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 의견 등록. 방 존재/만료/입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class CreateFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;

    // 같은 회원이 이 간격 안에 다시 등록하면 429. 오타를 고쳐 다시 쓰는 정도는 막지 않으면서
    // 자동화된 반복 등록은 걸러내는 선으로 잡았다.
    @Value("${feedback.rate-limit-window:1m}")
    private Duration rateLimitWindow;

    @Transactional
    public Feedback create(
        Long roomId,
        Long memberId,
        String content,
        String rawUserAgent,
        String rawAppVersion
    ) {
        FeedbackContent feedbackContent = new FeedbackContent(content);
        requireNotTooFrequent(memberId);

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        String nickname = memberRepository.findById(memberId)
            .map(Member::getDisplayName)
            .map(displayName -> displayName.value())
            .orElse(null);

        return feedbackRepository.save(Feedback.write(
            feedbackContent,
            roomId,
            room.getName().value(),
            memberId,
            nickname,
            UserAgent.from(rawUserAgent),
            AppVersion.from(rawAppVersion),
            Instant.now()
        ));
    }

    private void requireNotTooFrequent(Long memberId) {
        Instant now = Instant.now();
        Optional<Feedback> latest =
            feedbackRepository.findLatestByMemberIdSince(memberId, now.minus(rateLimitWindow));
        if (latest.isEmpty()) {
            return;
        }
        Instant nextAllowedAt = latest.get().getCreatedAt().plus(rateLimitWindow);
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(
            Duration.between(now, nextAllowedAt).toMillis() / 1000.0));
        throw new FeedbackRateLimitedException(retryAfterSeconds);
    }
}
