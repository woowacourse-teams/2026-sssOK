package com.sssok.renewal.incoming.organization;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.application.organization.job.OrganizeEventAck;
import com.sssok.renewal.application.organization.job.OrganizeEventService;
import com.sssok.renewal.application.organization.job.OrganizeJobException;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원 인증과 별개인 서비스 인증(Bearer 토큰, 상수 시간 비교). 공개 nginx 에서 /internal/ 은 막고 사설 경로로만 연다.
// 토큰이 비어 있으면 어떤 요청도 통과시키지 않는다.
@Hidden
@RestController
@RequestMapping("/internal/gallery-analysis")
@RequiredArgsConstructor
public class GalleryAnalysisEventController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final OrganizeJobProperties properties;
    private final OrganizeEventService eventService;

    @PostMapping("/events")
    public OrganizeEventAck receive(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody GalleryAnalysisEventRequest request
    ) {
        requireServiceToken(authorization);
        if (request.jobId() == null || request.roomId() == null || request.executionId() == null
            || request.attempt() == null || request.state() == null || request.jobType() == null) {
            throw new OrganizeJobException(ErrorCode.INVALID_REQUEST_BODY);
        }
        return eventService.handle(request.toEvent());
    }

    private void requireServiceToken(String authorization) {
        String expected = properties.internalToken();
        if (expected == null || expected.isBlank() || authorization == null
            || !authorization.startsWith(BEARER_PREFIX)
            || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("서비스 인증에 실패했습니다");
        }
    }
}
