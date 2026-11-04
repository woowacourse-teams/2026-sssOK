package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.organization.interpret.InterpretOrganizeRequestService;
import com.sssok.renewal.application.organization.interpret.InterpretationException;
import com.sssok.renewal.application.organization.interpret.OrganizeInterpretation;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolCatalog;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 버튼 시작과 문장 시작이 같은 흐름을 쓴다: 활성 작업 확인 → 대상 사진 확정 → 도구 결정 → 저장.
// 두 방식의 차이는 도구를 정하는 방법뿐이다. 버튼은 종류에 대응하는 도구를 쓰고, 문장은 모델이 고른다.
// 외부 호출(MCP 목록, 모델)이 있어서 이 클래스는 트랜잭션을 잡지 않는다. 저장만 OrganizeJobCreator 가 한 트랜잭션으로 한다.
@Service
@RequiredArgsConstructor
public class OrganizeStartService {
    private static final int MAX_TEXT_LENGTH = 1000;

    private final OrganizeJobProperties properties;
    private final OrganizeTargetPhotoSelector selector;
    private final OrganizeJobCreator creator;
    private final OrganizeRateLimiter rateLimiter;
    private final OrganizeJobRepository repository;
    private final OrganizeIdempotency idempotency;
    private final OrganizeMemberPolicy memberPolicy;
    private final OrganizeJobExpiry expiry;
    private final ObjectProvider<McpOrganizeToolCatalog> catalog;
    private final ObjectProvider<InterpretOrganizeRequestService> interpreter;
    @Qualifier("organizeClock")
    private final Clock clock;

    public OrganizeJob startByKind(Long roomId, Long memberId, OrganizeKind kind, String idempotencyKey) {
        requireAvailable();
        String key = idempotency.requireKey(idempotencyKey);
        String hash = OrganizeIdempotency.hash("BUTTON:" + kind);
        Optional<OrganizeJob> replay = replayOf(memberId, roomId, key, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        precheck(roomId, memberId);
        OrganizeTargetPhotoSelector.Selection selection = selectPhotos(roomId);
        OrganizeTool tool = findTool(kind);
        return create(new OrganizeJobCreator.Request(roomId, memberId, kind, OrganizeTrigger.BUTTON, null, tool,
            selection, key, hash));
    }

    public OrganizeJob startByText(Long roomId, Long memberId, String text, String idempotencyKey) {
        requireAvailable();
        InterpretOrganizeRequestService service = interpreter.getIfAvailable();
        if (service == null) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
        String requestText = validText(text);
        String key = idempotency.requireKey(idempotencyKey);
        String hash = OrganizeIdempotency.hash("TEXT:" + requestText);
        // 재전송은 추가 접수가 아니므로 해석 횟수 제한과 접수 간격보다 먼저 확인한다.
        Optional<OrganizeJob> replay = replayOf(memberId, roomId, key, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        precheck(roomId, memberId);
        OrganizeTargetPhotoSelector.Selection selection = selectPhotos(roomId);
        // 모델을 실제로 부르는 시도만 분당 횟수에 센다. 위에서 거절된 요청은 모델을 부르지 않았다.
        rateLimiter.acquire(memberId);
        OrganizeTool tool = interpret(service, requestText);
        OrganizeKind kind = properties.kindOf(tool.name());
        if (kind == null) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNSUPPORTED_REQUEST);
        }
        return create(new OrganizeJobCreator.Request(roomId, memberId, kind, OrganizeTrigger.TEXT, requestText, tool,
            selection, key, hash));
    }

    // 기한이 지난 READY 를 먼저 정리하고, 회원 사용 정책(익명 체험·접수 간격)과 방 활성 작업을 읽기만으로 사전 확인한다.
    private void precheck(Long roomId, Long memberId) {
        expiry.expireActiveOfRoom(roomId);
        memberPolicy.precheck(memberId, roomId, clock.instant());
        rejectIfActive(roomId, memberId);
    }

    private Optional<OrganizeJob> replayOf(Long memberId, Long roomId, String key, String hash) {
        return idempotency.find(memberId, roomId, OrganizeIdempotency.START, key, hash)
            .flatMap(found -> repository.findById(found.jobId()))
            .map(expiry::refresh);
    }

    private void requireAvailable() {
        if (!properties.enabled()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
    }

    private String validText(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_TEXT_LENGTH) {
            throw new OrganizeJobException(ErrorCode.INVALID_PARAM, "정리 요청은 1~" + MAX_TEXT_LENGTH + "자여야 합니다");
        }
        return stripped;
    }

    private void rejectIfActive(Long roomId, Long memberId) {
        repository.findActiveByRoom(roomId).ifPresent(active -> {
            throw OrganizeJobInProgressException.of(active, memberId);
        });
    }

    private OrganizeTargetPhotoSelector.Selection selectPhotos(Long roomId) {
        OrganizeTargetPhotoSelector.Selection selection = selector.select(roomId);
        int count = selection.photos().size();
        if (count == 0) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_NO_ANALYZABLE_PHOTOS);
        }
        if (count > properties.maxPhotos()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_TOO_MANY_PHOTOS, count, properties.maxPhotos());
        }
        return selection;
    }

    private OrganizeTool findTool(OrganizeKind kind) {
        McpOrganizeToolCatalog toolCatalog = catalog.getIfAvailable();
        if (toolCatalog == null) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
        String toolName = properties.toolNameOf(kind);
        try {
            return toolCatalog.fetchTools().stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE));
        } catch (InterpretationException failure) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
    }

    private OrganizeTool interpret(InterpretOrganizeRequestService service, String text) {
        OrganizeInterpretation interpretation;
        try {
            interpretation = service.interpret(text);
        } catch (InterpretationException failure) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
        return switch (interpretation) {
            case OrganizeInterpretation.ToolSelected selected -> selected.tool();
            case OrganizeInterpretation.NeedsClarification clarification ->
                throw new OrganizeJobException(ErrorCode.ORGANIZE_NEEDS_CLARIFICATION);
            case OrganizeInterpretation.Unsupported unsupported ->
                throw new OrganizeJobException(ErrorCode.ORGANIZE_UNSUPPORTED_REQUEST);
        };
    }

    // 동시에 두 요청이 들어오면 위의 확인을 둘 다 통과할 수 있다. 그때는 DB 유니크 제약이 한쪽을 거절한다.
    // 같은 키의 동시 재전송이면 먼저 저장된 작업을, 다른 요청이면 방이 사용 중이라고 답한다.
    private OrganizeJob create(OrganizeJobCreator.Request request) {
        try {
            return creator.create(request);
        } catch (DataIntegrityViolationException conflict) {
            Optional<OrganizeJob> replay = replayOf(request.memberId(), request.roomId(), request.idempotencyKey(),
                request.requestHash());
            if (replay.isPresent()) {
                return replay.get();
            }
            Optional<OrganizeJob> active = repository.findActiveByRoom(request.roomId());
            if (active.isEmpty()) {
                throw conflict;
            }
            throw OrganizeJobInProgressException.of(active.get(), request.memberId());
        }
    }
}
