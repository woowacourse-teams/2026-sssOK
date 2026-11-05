package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.media.MediaIdsResolver;
import com.sssok.application.media.ResolvedMediaIds;
import com.sssok.application.mediafolder.AddMediaToFoldersResult;
import com.sssok.application.mediafolder.AddMediaToFoldersService;
import com.sssok.common.exception.ErrorCode;
import com.sssok.domain.folder.Folder;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 검토한 그룹을 새 폴더로 반영한다. 폴더 생성·사진 연결·APPLIED·방 해제가 모두 한 트랜잭션이라 일부만 반영되지 않는다.
// 반영할 수 있는 것은 groupId 가 있는 일반 그룹뿐이다. 같은 이름의 기존 폴더에 덮어쓰거나 병합하지 않는다.
@Service
public class OrganizeApplyService {
    private static final TypeReference<List<AppliedFolder>> FOLDERS = new TypeReference<>() {
    };

    private final OrganizeJobQueryService queryService;
    private final OrganizeIdempotency idempotency;
    private final OrganizeJobRepository repository;
    private final OrganizeChangeNotifier notifier;
    private final CreateFolderService createFolderService;
    private final AddMediaToFoldersService addMediaToFoldersService;
    private final MediaIdsResolver mediaIdsResolver;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OrganizeApplyService(OrganizeJobQueryService queryService, OrganizeIdempotency idempotency,
                                OrganizeJobRepository repository, OrganizeChangeNotifier notifier,
                                CreateFolderService createFolderService,
                                AddMediaToFoldersService addMediaToFoldersService,
                                MediaIdsResolver mediaIdsResolver, ObjectMapper objectMapper,
                                @Qualifier("organizeClock") Clock clock, PlatformTransactionManager transactionManager) {
        this.queryService = queryService;
        this.idempotency = idempotency;
        this.repository = repository;
        this.notifier = notifier;
        this.createFolderService = createFolderService;
        this.addMediaToFoldersService = addMediaToFoldersService;
        this.mediaIdsResolver = mediaIdsResolver;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public record GroupRequest(String groupId, String folderName, List<Long> mediaIds) {
    }

    public record AppliedFolder(Long folderId, String folderName, List<Long> mediaIds) {
    }

    public record Applied(OrganizeJob job, List<AppliedFolder> folders) {
    }

    public Applied apply(Long roomId, Long memberId, UUID jobId, List<GroupRequest> groups, String idempotencyKey) {
        String key = idempotency.requireKey(idempotencyKey);
        List<GroupRequest> requested = validate(groups);
        String hash = OrganizeIdempotency.hash("APPLY:" + jobId + ":" + canonical(requested));
        OrganizeJob job = queryService.get(roomId, memberId, jobId);
        Optional<OrganizeIdempotency.Replay> replay =
            idempotency.find(memberId, roomId, OrganizeIdempotency.APPLY, key, hash);
        if (replay.isPresent()) {
            return new Applied(job, read(replay.get().response()));
        }
        requireApplicable(job);
        selectInResult(job, requested);
        List<AppliedFolder> folders = transaction.execute(status -> applyInTransaction(job, requested, key, hash));
        return new Applied(queryService.get(roomId, memberId, jobId), folders);
    }

    // ---- 요청 검증 ----

    private List<GroupRequest> validate(List<GroupRequest> groups) {
        if (groups == null || groups.isEmpty()) {
            throw invalid("반영할 그룹을 1개 이상 선택해주세요");
        }
        Set<String> groupIds = new HashSet<>();
        List<GroupRequest> normalized = new ArrayList<>();
        for (GroupRequest group : groups) {
            if (group == null || isBlank(group.groupId()) || isBlank(group.folderName())
                || group.mediaIds() == null || group.mediaIds().isEmpty()
                || group.mediaIds().stream().anyMatch(id -> id == null)) {
                throw invalid("그룹 ID, 폴더 이름, 사진을 모두 입력해주세요");
            }
            if (!groupIds.add(group.groupId())) {
                throw invalid("같은 그룹을 두 번 선택할 수 없습니다");
            }
            if (new HashSet<>(group.mediaIds()).size() != group.mediaIds().size()) {
                throw invalid("한 그룹 안에서 같은 사진을 두 번 선택할 수 없습니다");
            }
            normalized.add(new GroupRequest(group.groupId(), group.folderName().strip(), List.copyOf(group.mediaIds())));
        }
        return normalized;
    }

    private String canonical(List<GroupRequest> groups) {
        List<GroupRequest> ordered = groups.stream()
            .sorted((a, b) -> a.groupId().compareTo(b.groupId()))
            .map(group -> new GroupRequest(group.groupId(), group.folderName(), group.mediaIds().stream().sorted().toList()))
            .toList();
        try {
            // 구분자가 포함된 그룹 ID·폴더 이름도 서로 다른 입력으로 구분한다.
            return objectMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("반영 요청을 직렬화할 수 없습니다", failure);
        }
    }

    private void requireApplicable(OrganizeJob job) {
        Instant now = clock.instant();
        switch (job.status()) {
            case APPLIED -> throw new OrganizeJobException(ErrorCode.ORGANIZE_ALREADY_APPLIED);
            case EXPIRED -> throw new OrganizeJobException(ErrorCode.ORGANIZE_REVIEW_EXPIRED);
            case READY -> {
                if (job.isReviewOverdue(now)) {
                    throw new OrganizeJobException(ErrorCode.ORGANIZE_REVIEW_EXPIRED);
                }
                if (job.isResultExpired(now)) {
                    throw new OrganizeJobException(ErrorCode.ORGANIZE_RESULT_EXPIRED);
                }
            }
            default -> throw new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_READY);
        }
    }

    // 선택한 그룹은 이 작업 결과의 일반 그룹이어야 하고, 사진은 그 그룹에 속해야 한다. 소그룹·미분류는 반영 대상이 아니다.
    private void selectInResult(OrganizeJob job, List<GroupRequest> requested) {
        OrganizeResult stored = OrganizeResult.parse(objectMapper, job.result());
        for (GroupRequest group : requested) {
            OrganizeResult.Group found = stored.findGroup(group.groupId()).orElseThrow(() ->
                invalid("반영할 수 없는 그룹입니다. 일반 그룹만 선택할 수 있습니다: " + group.groupId()));
            if (!found.containsAll(group.mediaIds())) {
                throw invalid("그룹에 속하지 않은 사진이 있습니다: " + group.groupId());
            }
        }
    }

    // ---- 반영 ----

    // 먼저 APPLIED 로 바꿔 취소·만료와의 경합을 정리한 뒤 폴더를 만든다. 이후 어떤 단계가 실패해도 전체가 되돌아간다.
    private List<AppliedFolder> applyInTransaction(OrganizeJob job, List<GroupRequest> requested, String key,
                                                   String hash) {
        Instant now = clock.instant();
        if (!repository.markJobApplied(job.id(), now)) {
            requireApplicable(repository.findById(job.id()).orElseThrow());
            throw new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_READY);
        }
        List<AppliedFolder> folders = new ArrayList<>();
        for (GroupRequest group : requested) {
            folders.add(createFolder(job, group));
        }
        idempotency.record(job.requesterId(), job.roomId(), OrganizeIdempotency.APPLY, key, hash, job.id(),
            write(folders), now);
        notifier.changed(job.id(), job.roomId(), Occupancy.FREE);
        return folders;
    }

    private AppliedFolder createFolder(OrganizeJob job, GroupRequest group) {
        ResolvedMediaIds resolved = mediaIdsResolver.resolveVisible(job.roomId(), group.mediaIds());
        if (resolved.files().size() != group.mediaIds().size()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_TARGET_CHANGED);
        }
        Folder folder;
        try {
            folder = createFolderService.create(job.roomId(), group.folderName());
        } catch (DuplicateFolderNameException duplicate) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_FOLDER_NAME_CONFLICT);
        }
        AddMediaToFoldersResult added = addMediaToFoldersService.add(job.roomId(), group.mediaIds(), folder.getId());
        if (added.updatedCount() != group.mediaIds().size()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_TARGET_CHANGED);
        }
        return new AppliedFolder(folder.getId(), folder.getName().value(), group.mediaIds());
    }

    private OrganizeJobException invalid(String message) {
        return new OrganizeJobException(ErrorCode.INVALID_PARAM, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String write(List<AppliedFolder> folders) {
        try {
            return objectMapper.writeValueAsString(folders);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("반영 결과를 저장할 수 없습니다", failure);
        }
    }

    private List<AppliedFolder> read(String response) {
        try {
            return objectMapper.readValue(response, FOLDERS);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("저장된 반영 결과를 읽을 수 없습니다", failure);
        }
    }
}
