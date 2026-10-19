package com.sssok.application.feedback;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.application.feedback.exception.FeedbackNotFoundException;
import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 의견 처리 상태 변경.
@Service
@RequiredArgsConstructor
public class FeedbackStatusService {

    private final FeedbackRepository feedbackRepository;
    private final AdminPermissionChecker permissionChecker;

    @Transactional
    public Feedback changeStatus(Long adminId, Long feedbackId, String status) {
        permissionChecker.require(adminId, AdminPermission.FEEDBACK_MANAGE);
        FeedbackStatus newStatus = FeedbackStatus.from(status);

        Feedback feedback = feedbackRepository.findById(feedbackId).orElseThrow(FeedbackNotFoundException::new);
        feedback.changeStatus(newStatus);
        return feedbackRepository.save(feedback);
    }
}
