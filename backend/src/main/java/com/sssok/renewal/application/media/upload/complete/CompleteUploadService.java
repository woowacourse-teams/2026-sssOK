package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.Stage.*;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.room.RoomNicknameReader;
import com.sssok.renewal.application.media.upload.shared.UploadRoomValidator;
import com.sssok.renewal.outgoing.database.UploadMemberRepository;
import com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

// 스토리지 HEAD 확인은 트랜잭션 밖에서 수행한다. DB 쓰기는 별도 빈인 MediaRegistrationService가 담당한다.
@Service
@RequiredArgsConstructor
public class CompleteUploadService {
    private final UploadRegistrationMetrics metrics;
    private final UploadRoomValidator uploadRoomValidator;
    private final CompleteUploadValidator completeUploadValidator;
    private final RoomNicknameReader roomNicknameReader;
    private final MediaRegistrationService mediaRegistrationService;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteUploadResult complete(CompleteUploadCommand command) {
        return metrics.total(command.mediaIds() == null ? 0 : command.mediaIds().size(), () -> completeMeasured(command),
            result -> result.failed().size());
    }

    private CompleteUploadResult completeMeasured(CompleteUploadCommand command) {
        command.validate();
        metrics.measure(ACCESS, () -> uploadRoomValidator.validate(command.roomId(), command.uploaderId()));
        ValidatedUploadedFiles validatedFiles = metrics.measure(VALIDATE, () -> completeUploadValidator.validate(command));
        String uploaderName = metrics.measure(NICKNAME, () -> roomNicknameReader.read(command.roomId(), command.uploaderId()));
        List<MediaDetail> registeredMedia = metrics.measure(REGISTRATION, () -> mediaRegistrationService.register(
            command.roomId(), validatedFiles.verifiedFiles(), uploaderName));
        metrics.measure(UPLOADED_PUBLISH, () -> publishUploaded(command, uploaderName, registeredMedia.size()));
        return new CompleteUploadResult(registeredMedia, validatedFiles.failedMedia());
    }

    // 등록 트랜잭션이 커밋된 뒤라 롤백된 업로드를 알릴 일은 없다. 재시도로 새로 등록된 것이 없으면 알리지 않는다.
    private void publishUploaded(CompleteUploadCommand command, String uploaderName, int registeredCount) {
        if (registeredCount > 0) {
            eventPublisher.publishEvent(
                new MediaUploadedEvent(command.roomId(), command.uploaderId(), uploaderName));
        }
    }
}
