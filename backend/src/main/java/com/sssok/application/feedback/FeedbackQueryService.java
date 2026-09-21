package com.sssok.application.feedback;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.application.feedback.exception.FeedbackNotFoundException;
import com.sssok.application.feedback.exception.InvalidFeedbackCursorException;
import com.sssok.application.feedback.exception.InvalidFeedbackPageSizeException;
import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.domain.feedback.Feedback;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 의견 조회.
@Service
@RequiredArgsConstructor
public class FeedbackQueryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final FeedbackRepository feedbackRepository;
    private final AdminPermissionChecker permissionChecker;

    @Transactional(readOnly = true)
    public FeedbackPage findLatest(Long adminId, Long cursor, Integer size) {
        permissionChecker.require(adminId, AdminPermission.FEEDBACK_READ);

        int pageSize = pageSizeOf(size);
        // 한 건 더 읽어 다음 페이지가 있는지 판정한다. 전체 건수를 세지 않아도 된다.
        List<Feedback> found = readPage(cursor, pageSize + 1);
        boolean hasNext = found.size() > pageSize;
        return FeedbackPage.of(hasNext ? found.subList(0, pageSize) : found, hasNext);
    }

    @Transactional(readOnly = true)
    public Feedback findById(Long adminId, Long feedbackId) {
        permissionChecker.require(adminId, AdminPermission.FEEDBACK_READ);
        return feedbackRepository.findById(feedbackId).orElseThrow(FeedbackNotFoundException::new);
    }

    // 커서가 가리키는 의견의 정렬 키(작성 시각, ID)로 자른다. 정렬 기준과 커서 기준을 일치시켜
    // 동시에 등록돼 작성 시각과 ID 의 순서가 어긋난 의견도 정확히 한 번만 나오게 한다.
    private List<Feedback> readPage(Long cursor, int limit) {
        if (cursor == null) {
            return feedbackRepository.findLatest(limit);
        }
        Feedback cursorFeedback = feedbackRepository.findById(cursor)
            .orElseThrow(InvalidFeedbackCursorException::new);
        return feedbackRepository.findLatestBefore(
            cursorFeedback.getCreatedAt(), cursorFeedback.getId(), limit);
    }

    private int pageSizeOf(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFeedbackPageSizeException();
        }
        return size;
    }
}
