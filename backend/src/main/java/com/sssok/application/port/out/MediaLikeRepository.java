package com.sssok.application.port.out;

import com.sssok.domain.file.UploadStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 사진 좋아요(media_like) 영속화 출력. 순수 조인 관계라 도메인 객체 없이 ID로만 다룬다.
public interface MediaLikeRepository {

    // 미디어가 주어진 상태일 때만 좋아요를 남긴다. 새로 남겼으면 true, 이미 눌렀거나 상태가 맞지 않으면 false 다.
    boolean likeIfStatusIn(Long mediaId, Long memberId, Collection<UploadStatus> statuses);

    // 눌러 둔 좋아요를 지운다. 실제로 지웠으면 true, 원래 누르지 않았으면 false 다.
    boolean unlike(Long mediaId, Long memberId);

    long countByMediaId(Long mediaId);

    // 미디어별 좋아요 수. 아무도 누르지 않은 미디어는 결과에 없다.
    Map<Long, Long> countByMediaIds(List<Long> mediaIds);

    // 주어진 미디어 중 이 회원이 좋아요를 누른 것만 돌려준다.
    Set<Long> findLikedMediaIds(Long memberId, List<Long> mediaIds);

    // 미디어를 지울 때 그 미디어에 붙은 좋아요를 함께 지운다.
    void deleteAllByMediaIds(List<Long> mediaIds);
}
