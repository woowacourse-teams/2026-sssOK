package com.sssok.presentation.api.feedback;

import com.sssok.application.feedback.FeedbackPage;
import com.sssok.application.feedback.FeedbackQueryService;
import com.sssok.domain.feedback.Feedback;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 의견 조회는 관리자만 할 수 있어 전부 /admin 아래에 둔다.
@Tag(name = "관리자 의견", description = "관리자가 사용자 의견을 읽는다. 일반 관리자와 슈퍼관리자 모두 조회할 수 있다.")
@RestController
@RequestMapping("/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackQueryService feedbackQueryService;

    @Operation(
        summary = "의견 목록 조회",
        description = """
            사용자 의견을 최신순으로 조회한다. 건수가 계속 늘어나므로 커서 기반 페이지네이션을 둔다. \
            cursor 를 생략하면 가장 최신부터 내려주고, 다음 페이지는 직전 응답의 nextCursor 를 그대로 \
            넣어 요청한다. size 는 기본 20, 최대 100 이며 범위를 벗어나면 400이 난다.

            목록의 content 는 100자까지만 내려준다. 전문은 단건 조회에서 본다.

            방 이름·닉네임은 작성 시점의 값 복사본이라 그 방이 이미 영구 삭제됐어도 그대로 보인다. \
            다만 roomId 로 방을 다시 조회하면 없을 수 있어, 살아 있는 방으로 연결하는 링크로 써서는 안 된다.

            userAgent·frontendVersion 은 null 이 정상 값이다. 헤더 없이 들어온 의견(curl·봇·구버전 \
            클라이언트)이 있어서, 값이 없다고 조회가 실패하거나 항목이 빠지지 않는다.

            익명 사용자 토큰으로 호출하면 관리자 토큰이 아니라 토큰 단계에서 401로 걸린다.\
            """
    )
    @GetMapping
    public ApiResponse<FeedbacksResponse> findLatest(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "직전 응답의 nextCursor. 없으면 최신부터")
        @RequestParam(required = false) Long cursor,
        @Parameter(description = "한 번에 가져올 개수. 기본 20, 최대 100")
        @RequestParam(required = false) Integer size
    ) {
        FeedbackPage page = feedbackQueryService.findLatest(adminId, cursor, size);
        return ApiResponse.of(FeedbacksResponse.from(page));
    }

    @Operation(
        summary = "의견 단건 조회",
        description = """
            의견 하나의 전문과 맥락을 조회한다. 목록에서는 본문이 잘려 나오므로 전체를 읽으려면 이 API 를 쓴다. \
            userAgent 도 자르지 않고 저장된 원본 그대로 내려준다.

            맥락 값은 작성 시점 복사본이라 원본 방·회원이 사라져도 조회에 실패하지 않는다. \
            없는 의견 식별자로 조회하면 404가 난다.\
            """
    )
    @GetMapping("/{feedbackId}")
    public ApiResponse<FeedbackResponse> findById(
        @Parameter(hidden = true) @AuthAdmin Long adminId,
        @Parameter(description = "조회할 의견의 ID") @PathVariable Long feedbackId
    ) {
        Feedback feedback = feedbackQueryService.findById(adminId, feedbackId);
        return ApiResponse.of(FeedbackResponse.from(feedback));
    }
}
