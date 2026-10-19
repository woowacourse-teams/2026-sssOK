package com.sssok.application.feedback;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.application.feedback.exception.FeedbackNotFoundException;
import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackAssignee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 의견 담당자 지정·해제.
@Service
@RequiredArgsConstructor
public class FeedbackAssigneeService {

    private final FeedbackRepository feedbackRepository;
    private final AdminPermissionChecker permissionChecker;

    @Transactional
    public Feedback changeAssignee(Long adminId, Long feedbackId, String assignee) {
        permissionChecker.require(adminId, AdminPermission.FEEDBACK_MANAGE);
        FeedbackAssignee newAssignee = assignee == null ? null : FeedbackAssignee.from(assignee);

        Feedback feedback = feedbackRepository.findById(feedbackId).orElseThrow(FeedbackNotFoundException::new);
        feedback.changeAssignee(newAssignee);
        return feedbackRepository.save(feedback);
    }
}
