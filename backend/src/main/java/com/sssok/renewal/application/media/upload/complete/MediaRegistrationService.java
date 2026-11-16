package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.Stage.*;

import com.sssok.application.media.MediaCreatedEvent;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.MediaUrlResolver;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.MediaNotFoundException;
import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.media.thumbnail.ThumbnailJobService;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.database.UploadFolderRepository;
import com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaRegistrationService {

    private final UploadRegistrationMetrics metrics;
    private final UploadFileRepository uploadFileRepository;
    private final ThumbnailJobService thumbnailJobService;
    private final UploadFolderRepository uploadFolderRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaLikeRepository mediaLikeRepository;

    // 스토리지 검증은 호출 전에 끝낸다. 파일 상태와 썸네일 작업은 함께 커밋하거나 롤백한다.
    @Transactional
    public RegistrationResult register(Long roomId, List<StoredFile> verifiedFiles, String uploaderName) {
        if (verifiedFiles.isEmpty()) {
            return new RegistrationResult(List.of(), 0);
        }
        List<Long> mediaIds = verifiedFiles.stream().map(StoredFile::getId).toList();
        Map<Long, List<Long>> folderIdsByMediaId = metrics.measure(FOLDERS,
            () -> uploadFolderRepository.findFolderIdsByMedia(mediaIds));
        RegistrationFiles registration = metrics.measure(FILE_WRITE,
            () -> registerForProcessing(roomId, verifiedFiles));
        MediaLikeRepository.LikeSummary likes = loadExistingLikes(registration, verifiedFiles.getFirst().getUploaderId());
        List<MediaDetail> details = toDetails(registration.registered(), uploaderName, folderIdsByMediaId, likes);
        publishMediaCreatedEvents(roomId, details.stream()
            .filter(media -> registration.isNew(media.mediaId())).toList());
        return new RegistrationResult(details, registration.newlyRegisteredIds().size());
    }

    // 신규 파일의 좋아요는 아직 없다. 재요청에 포함된 기존 파일만 현재 값을 읽는다.
    private MediaLikeRepository.LikeSummary loadExistingLikes(RegistrationFiles registration, Long uploaderId) {
        List<Long> existingIds = registration.registered().stream().map(StoredFile::getId)
            .filter(id -> !registration.isNew(id)).toList();
        return existingIds.isEmpty()
            ? new MediaLikeRepository.LikeSummary(Map.of(), Set.of())
            : mediaLikeRepository.summarize(uploaderId, existingIds);
    }

    private List<MediaDetail> toDetails(List<StoredFile> files, String uploaderName,
                                       Map<Long, List<Long>> folderIdsByMediaId,
                                       MediaLikeRepository.LikeSummary likes) {
        return files.stream().map(file -> metrics.measure(RESPONSE, () -> {
            List<Long> folderIds = folderIdsByMediaId.getOrDefault(file.getId(), List.of());
            String nickname = file.getUploaderNickname() == null ? uploaderName : file.getUploaderNickname();
            return MediaDetail.of(file, nickname, folderIds, mediaUrlResolver.resolve(file),
                likes.counts().getOrDefault(file.getId(), 0L), likes.likedIds().contains(file.getId()));
        })).toList();
    }

    // 동시 요청도 최신 상태로 판정하도록 ID 순서로 잠근 뒤 신규·기존 등록을 구분한다.
    private RegistrationFiles registerForProcessing(Long roomId, List<StoredFile> verifiedFiles) {
        List<Long> ids = verifiedFiles.stream().map(StoredFile::getId).toList();
        Map<Long, StoredFile> lockedFiles = uploadFileRepository.findAllForRegistration(ids).stream()
            .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        List<StoredFile> registered = new ArrayList<>();
        List<StoredFile> newlyRegistered = new ArrayList<>();
        for (StoredFile verified : verifiedFiles) {
            StoredFile file = Optional.ofNullable(lockedFiles.get(verified.getId()))
                .filter(found -> found.getRoomId().equals(roomId))
                .orElseThrow(MediaNotFoundException::new);
            if (!file.isUploadedBy(verified.getUploaderId())) {
                throw new MediaForbiddenException();
            }
            if (!file.getStatus().isVisible()) {
                // HEAD를 생략한 등록 완료분이 그 사이 FAILED로 바뀌면 다시 검증해야 한다.
                if (verified.getStatus().isVisible()) {
                    throw new UploadVerificationUnavailableException();
                }
                file.startProcessing();
                file = uploadFileRepository.save(file);
                newlyRegistered.add(file);
            }
            registered.add(file);
        }
        if (!newlyRegistered.isEmpty()) {
            thumbnailJobService.enqueueAll(newlyRegistered);
        }
        return new RegistrationFiles(registered,
            newlyRegistered.stream().map(StoredFile::getId).collect(Collectors.toSet()));
    }

    private record RegistrationFiles(List<StoredFile> registered, Set<Long> newlyRegisteredIds) {
        boolean isNew(Long mediaId) {
            return newlyRegisteredIds.contains(mediaId);
        }
    }

    public record RegistrationResult(List<MediaDetail> registered, int newlyRegisteredCount) { }

    private void publishMediaCreatedEvents(Long roomId, List<MediaDetail> registeredMedia) {
        // 이벤트는 현재 트랜잭션에 등록하고, 기존 AFTER_COMMIT 리스너가 커밋 후 SSE로 발행한다.
        for (MediaDetail media : registeredMedia) {
            eventPublisher.publishEvent(new MediaCreatedEvent(roomId, media));
        }
    }
}
