package com.sssok.application.medialike;

import com.sssok.application.port.out.MediaLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 좋아요 취소. 누르지 않은 사진에 보내도 오류 없이 같은 결과를 돌려준다(멱등).
// 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class UnlikeMediaService {

    private final LikeTargets likeTargets;
    private final MediaLikeRepository mediaLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MediaLikeResult unlike(Long roomId, Long mediaId, Long memberId) {
        likeTargets.requireVisibleInRoom(roomId, mediaId);

        if (mediaLikeRepository.unlike(mediaId, memberId)) {
            eventPublisher.publishEvent(new MediaLikeChangedEvent(roomId, mediaId));
        }
        return new MediaLikeResult(mediaId, false, mediaLikeRepository.countByMediaId(mediaId));
    }
}
