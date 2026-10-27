package com.sssok.renewal.application.media.upload.complete;

import com.sssok.renewal.application.media.upload.shared.UploadRoomValidator;
import com.sssok.application.media.MediaDetail;
import com.sssok.renewal.outgoing.database.UploadMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

// 스토리지 HEAD 확인은 트랜잭션 밖에서 수행한다. DB 쓰기는 별도 빈인 MediaRegistrationService가 담당한다.
@Service
@RequiredArgsConstructor
public class CompleteUploadService {
    private final UploadRoomValidator uploadRoomValidator;
    private final CompleteUploadValidator completeUploadValidator;
    private final UploadMemberRepository uploadMemberRepository;
    private final MediaRegistrationService mediaRegistrationService;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteUploadResult complete(CompleteUploadCommand command) {
        command.validate();
        uploadRoomValidator.validate(command.roomId(), command.uploaderId());
        ValidatedUploadedFiles validatedFiles = completeUploadValidator.validate(command);
        String uploaderName = uploadMemberRepository.findById(command.uploaderId())
            .map(member -> member.getDisplayName().value()).orElse(null);
        List<MediaDetail> registeredMedia = mediaRegistrationService.register(
            command.roomId(), validatedFiles.verifiedFiles(), uploaderName);
        publishUploaded(command, uploaderName, registeredMedia.size());
        return new CompleteUploadResult(registeredMedia, validatedFiles.failedMedia());
    }

    // 등록 트랜잭션이 커밋된 뒤라 롤백된 업로드를 알릴 일은 없다. 재시도로 새로 등록된 것이 없으면 알리지 않는다.
    private void publishUploaded(CompleteUploadCommand command, String uploaderName, int registeredCount) {
        if (registeredCount > 0) {
            eventPublisher.publishEvent(
                new MediaUploadedEvent(command.roomId(), command.uploaderId(), uploaderName, registeredCount));
        }
    }
}
