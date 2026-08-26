package com.sssok.application.media;

import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FileStoragePort.UploadedObject;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadRejectionReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 스토리지에 실물이 있는지 서버가 직접 확인한다. 클라이언트 말만 믿으면 올리지 않은 파일이 목록에 뜬다.
@Service
@RequiredArgsConstructor
public class CompleteUploadService {

    private static final String MEDIA_CREATED = "media.created";

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final FolderMediaRepository folderMediaRepository;
    private final MemberRepository memberRepository;
    private final RoomPermissionPort roomPermissionPort;
    private final EventPublisherPort eventPublisherPort;

    @Transactional
    public CompleteUploadResult complete(Long roomId, Long uploaderId, List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new InvalidUploadParamException("등록할 미디어가 없습니다");
        }
        if (!roomPermissionPort.canUpload(roomId, uploaderId)) {
            throw new UploadNotAllowedException();
        }

        List<StoredFile> found = fileRepository.findAllByIdIn(mediaIds);
        rejectOthersReservation(found, uploaderId);

        List<MediaDetail> registered = new ArrayList<>();
        List<FailedMedia> failed = new ArrayList<>();
        String uploaderName = uploaderName(uploaderId);

        for (Long mediaId : mediaIds) {
            StoredFile file = findIn(found, mediaId);
            if (file == null) {
                failed.add(FailedMedia.of(mediaId, UploadRejectionReason.MEDIA_NOT_FOUND));
                continue;
            }
            UploadRejectionReason reason = verifyUploaded(file);
            if (reason != null) {
                failed.add(FailedMedia.of(mediaId, reason));
                continue;
            }
            file.startProcessing();
            StoredFile saved = fileRepository.save(file);
            registered.add(MediaDetail.of(saved, uploaderName,
                folderMediaRepository.findFolderIdsContainingMedia(List.of(mediaId))));
        }

        publishCreated(roomId, registered);
        return new CompleteUploadResult(registered, failed);
    }

    // 남이 예약한 mediaId 가 하나라도 섞여 있으면 요청 전체를 막는다. 남의 업로드를 건드리는 시도다.
    private void rejectOthersReservation(List<StoredFile> found, Long uploaderId) {
        boolean hasOthers = found.stream().anyMatch(file -> !file.isUploadedBy(uploaderId));
        if (hasOthers) {
            throw new MediaForbiddenException();
        }
    }

    // 발급 때 신고한 값과 실제로 올라온 것이 다르면 거부한다. 통과하면 null 이다.
    private UploadRejectionReason verifyUploaded(StoredFile file) {
        Optional<UploadedObject> uploaded = fileStoragePort.findUploaded(file.getStorageKey());
        if (uploaded.isEmpty()) {
            return UploadRejectionReason.UPLOAD_NOT_COMPLETED;
        }
        UploadedObject object = uploaded.get();
        if (!file.matchesUploaded(object.sizeBytes(), object.contentType())) {
            return object.sizeBytes() != file.getFileSize().bytes()
                ? UploadRejectionReason.FILE_TOO_LARGE
                : UploadRejectionReason.UNSUPPORTED_MEDIA_TYPE;
        }
        return null;
    }

    private StoredFile findIn(List<StoredFile> files, Long mediaId) {
        return files.stream()
            .filter(file -> file.getId().equals(mediaId))
            .findFirst()
            .orElse(null);
    }

    private String uploaderName(Long uploaderId) {
        return memberRepository.findById(uploaderId)
            .map(member -> member.getDisplayName().value())
            .orElse(null);
    }

    private void publishCreated(Long roomId, List<MediaDetail> registered) {
        registered.forEach(media -> eventPublisherPort.publish(roomId, MEDIA_CREATED, media));
    }
}
