package com.sssok.domain.feedback;

import com.sssok.domain.feedback.exception.InvalidFeedbackAssigneeException;
import java.util.Arrays;

// 의견을 맡아 처리할 팀원. 관리자 계정과는 별개다.
public enum FeedbackAssignee {

    MAICHAN,    // 마이찬
    HAENI,      // 해니
    YUNDOL,     // 윤돌
    HYUNMIBAP;  // 현미밥

    public static FeedbackAssignee from(String name) {
        return Arrays.stream(values())
            .filter(assignee -> assignee.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new InvalidFeedbackAssigneeException(name));
    }
}
