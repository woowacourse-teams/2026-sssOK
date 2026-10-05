package com.sssok.application.medialike;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 좋아요 추가. 이미 눌렀으면 아무것도 바꾸지 않고 같은 결과를 돌려준다(멱등).
// 방 존재/만료/입장 여부는 RoomMembershipInterceptor가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class LikeMediaService {

    private final LikeTargets likeTargets;
    private final MediaLikeRepository mediaLikeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MediaLikeResult like(Long roomId, Long mediaId, Long memberId) {
        likeTargets.requireVisibleInRoom(roomId, mediaId);

        boolean added = mediaLikeRepository.likeIfStatusIn(mediaId, memberId, UploadStatus.visibleStatuses());
        if (added) {
            eventPublisher.publishEvent(new MediaLikeChangedEvent(roomId, mediaId));
        } else {
            requireAlreadyLiked(mediaId, memberId);
        }
        return new MediaLikeResult(mediaId, true, mediaLikeRepository.countByMediaId(mediaId));
    }

    // 넣지 못한 이유는 둘 중 하나다 — 이미 눌러 뒀거나, 위에서 확인한 뒤 미디어가 FAILED 로 확정됐거나 지워졌다.
    // 뒤의 경우에 liked: true 를 돌려주면 화면과 실제가 어긋나므로 처음부터 없던 미디어처럼 404 로 낸다.
    private void requireAlreadyLiked(Long mediaId, Long memberId) {
        if (mediaLikeRepository.findLikedMediaIds(memberId, List.of(mediaId)).isEmpty()) {
            throw new MediaNotFoundException();
        }
    }
}
