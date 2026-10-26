package com.sssok.renewal.application.media.upload.complete;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.room.RoomNicknameReader;
import com.sssok.renewal.application.media.upload.shared.UploadRoomValidator;
import com.sssok.renewal.outgoing.database.UploadMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 스토리지 HEAD 확인은 트랜잭션 밖에서 수행한다. DB 쓰기는 별도 빈인 MediaRegistrationService가 담당한다.
@Service
@RequiredArgsConstructor
public class CompleteUploadService {
    private final UploadRoomValidator uploadRoomValidator;
    private final CompleteUploadValidator completeUploadValidator;
    private final RoomNicknameReader roomNicknameReader;
    private final MediaRegistrationService mediaRegistrationService;

    public CompleteUploadResult complete(CompleteUploadCommand command) {
        command.validate();
        uploadRoomValidator.validate(command.roomId(), command.uploaderId());
        ValidatedUploadedFiles validatedFiles = completeUploadValidator.validate(command);
        String uploaderName = roomNicknameReader.read(command.roomId(), command.uploaderId());
        List<MediaDetail> registeredMedia = mediaRegistrationService.register(
            command.roomId(), validatedFiles.verifiedFiles(), uploaderName);
        return new CompleteUploadResult(registeredMedia, validatedFiles.failedMedia());
    }
}
