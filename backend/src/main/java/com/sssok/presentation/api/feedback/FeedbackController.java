package com.sssok.presentation.api.feedback;

import com.sssok.application.feedback.CreateFeedbackService;
import com.sssok.domain.feedback.Feedback;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "의견", description = "사용자가 서비스에 대해 남기는 의견")
@RestController
@RequestMapping("/rooms/{roomId}/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    // 표준 헤더가 아니라 우리가 정한 이름이라 상수로 둔다.
    private static final String APP_VERSION_HEADER = "X-App-Version";

    private final CreateFeedbackService createFeedbackService;

    @Operation(
        summary = "의견 등록",
        description = """
            방에 입장한 사용자가 서비스에 대한 의견을 남긴다. 본문은 1~1000자여야 하며 \
            공백만으로 이루어지면 400이 난다.

            클라이언트가 본문으로 보내는 값은 content 하나뿐이다. 서버가 방 번호·방 이름·회원 번호·\
            닉네임·User-Agent·앱 버전·작성 시각을 함께 저장한다.

            방 이름과 닉네임은 참조가 아니라 작성 시점의 값 복사본으로 저장한다. 방은 만료 7일 뒤 \
            영구 삭제되지만 의견은 그 뒤에도 팀이 봐야 해서, 참조로 걸면 맥락이 함께 사라진다.

            기기·버전 정보는 헤더로 받는다. User-Agent 는 브라우저가 자동으로 붙이고, \
            X-App-Version(예: fe-v0.1.0)은 프론트가 직접 실어 보낸다. 둘 다 선택이라 없어도 \
            등록은 성공하고 해당 값만 비워 둔다 — 부가 정보 때문에 사용자 의견을 잃으면 안 된다. \
            두 값 모두 등록 시점에만 받을 수 있어서, 여기서 저장하지 않으면 관리자가 나중에 \
            의견을 볼 때 어떤 기기에서 어느 버전을 쓰고 있었는지 알 방법이 없다.

            같은 회원이 짧은 시간에 연속으로 등록하면 429가 나고 Retry-After 헤더에 다시 시도할 수 \
            있는 시각까지의 초가 담긴다. 입장하지 않은 사용자는 403, 없는 방은 404, \
            만료·삭제된 방은 410이 난다.\
            """
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateFeedbackResponse> createFeedback(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
        String userAgent,
        @Parameter(description = "프론트 버전 태그. 예: fe-v0.1.0")
        @RequestHeader(value = APP_VERSION_HEADER, required = false) String appVersion,
        @RequestBody CreateFeedbackRequest request
    ) {
        String content = request == null ? null : request.content();
        Feedback feedback =
            createFeedbackService.create(roomId, memberId, content, userAgent, appVersion);
        return ApiResponse.of(CreateFeedbackResponse.from(feedback));
    }
}
