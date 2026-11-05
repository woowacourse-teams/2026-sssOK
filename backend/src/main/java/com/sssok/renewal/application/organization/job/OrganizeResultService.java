package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.MediaDetailAssembler;
import com.sssok.application.port.out.FileRepository;
import com.sssok.common.exception.ErrorCode;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 저장된 결과에 갤러리 사진 정보를 붙여 돌려준다. 갤러리에 아직 로딩하지 않은 사진도 조회할 수 있다.
// 검토 중에 삭제된 사진은 응답에서 빼고 removedCount 로 알린다.
@Service
public class OrganizeResultService {
    private final OrganizeJobQueryService queryService;
    private final FileRepository fileRepository;
    private final MediaDetailAssembler assembler;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readOnlyTransaction;
    private final Clock clock;

    public OrganizeResultService(OrganizeJobQueryService queryService, FileRepository fileRepository,
                                 MediaDetailAssembler assembler, ObjectMapper objectMapper,
                                 @Qualifier("organizeClock") Clock clock, PlatformTransactionManager transactionManager) {
        this.queryService = queryService;
        this.fileRepository = fileRepository;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    private record Stored(OrganizeJob job, OrganizeResult result, MediaDetailAssembler.LoadedMedia loaded) {
    }

    // 사진 URL 서명은 CPU 를 오래 쓰므로 DB 조회만 읽기 트랜잭션에서 끝내고 서명은 커넥션을 돌려준 뒤 한다.
    // 검토 기한이 지난 READY 의 만료 처리는 쓰기라서 읽기 트랜잭션 밖에서 먼저 한다.
    public OrganizeResultView get(Long roomId, Long memberId, UUID jobId) {
        queryService.get(roomId, memberId, jobId);
        Stored stored = readOnlyTransaction.execute(status -> load(roomId, memberId, jobId));
        Map<Long, MediaDetail> details = new HashMap<>();
        assembler.toDetails(stored.loaded()).forEach(detail -> details.put(detail.mediaId(), detail));
        return assemble(stored.job(), stored.result(), details);
    }

    // 검토 중(READY)은 검토 기한 안에서, 반영된(APPLIED) 결과는 보관 기한 안에서만 읽을 수 있다.
    // 검토가 만료된 작업은 결과 본문이 남아 있어도 410 이다. 읽는 것이 기한을 늘리지는 않는다.
    private Stored load(Long roomId, Long memberId, UUID jobId) {
        OrganizeJob job = queryService.peek(roomId, memberId, jobId);
        Instant now = clock.instant();
        if (job.status() == OrganizeJobStatus.EXPIRED || job.isReviewOverdue(now)) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_REVIEW_EXPIRED);
        }
        if (job.status() != OrganizeJobStatus.READY && job.status() != OrganizeJobStatus.APPLIED) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_READY);
        }
        if (job.isResultExpired(now)) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_RESULT_EXPIRED);
        }
        OrganizeResult result = OrganizeResult.parse(objectMapper, job.result());
        List<StoredFile> files = fileRepository.findAllByRoomIdAndIdIn(roomId, result.referencedMediaIds()).stream()
            .filter(file -> UploadStatus.visibleStatuses().contains(file.getStatus()))
            .toList();
        return new Stored(job, result, assembler.load(files, memberId));
    }

    private OrganizeResultView assemble(OrganizeJob job, OrganizeResult result, Map<Long, MediaDetail> details) {
        int removed = (int) result.referencedMediaIds().stream().filter(id -> !details.containsKey(id)).count();
        List<OrganizeResultView.Group> groups = result.groups().stream()
            .map(group -> toGroup(group.groupId(), group.mediaIds(), details))
            .filter(group -> group.photoCount() > 0)
            .toList();
        List<OrganizeResultView.Group> small = job.kind() == OrganizeKind.SIMILAR
            ? result.smallGroupMediaIds().stream()
                .map(mediaIds -> toGroup(null, mediaIds, details))
                .filter(group -> group.photoCount() > 0)
                .toList()
            : null;
        List<OrganizeResultView.ErrorItem> errors = result.errors().stream()
            .map(error -> new OrganizeResultView.ErrorItem(error.mediaId(), error.code()))
            .toList();
        return new OrganizeResultView(job.publicId(), job.kind(), job.resultVersion() == null ? 1 : job.resultVersion(),
            job.photoCount(), removed, groups, small, toGroup(null, result.unassignedMediaIds(), details), errors);
    }

    // 삭제되어 상세를 못 찾은 사진은 빼고 남은 사진으로 그룹을 만든다.
    private OrganizeResultView.Group toGroup(String groupId, List<Long> mediaIds, Map<Long, MediaDetail> details) {
        List<MediaDetail> photos = mediaIds.stream().map(details::get).filter(Objects::nonNull).toList();
        return new OrganizeResultView.Group(groupId, photos.size(), photos);
    }
}
