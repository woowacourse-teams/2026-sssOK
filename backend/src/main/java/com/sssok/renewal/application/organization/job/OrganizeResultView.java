package com.sssok.renewal.application.organization.job;

import com.sssok.application.media.MediaDetail;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.util.List;
import java.util.UUID;

// 검토 화면용 결과. 사진 항목은 갤러리 목록 항목(MediaDetail)을 그대로 쓴다.
// smallGroups 는 SIMILAR 에만 있고(PERSON 은 null), 같은 사진이 여러 인물 그룹에 있을 수 있다.
public record OrganizeResultView(UUID jobId, OrganizeKind kind, int resultVersion, int photoCount,
                                 int removedCount, List<Group> groups, List<Group> smallGroups, Group unassigned,
                                 List<ErrorItem> errors) {

    public record Group(String groupId, int photoCount, List<MediaDetail> photos) {
    }

    public record ErrorItem(long mediaId, String code) {
    }
}
