package com.sssok.renewal.application.media.upload.issue;

import com.sssok.renewal.application.media.upload.shared.UploadUrl;
import com.sssok.renewal.application.media.upload.shared.UploadUrlIssuer;
import com.sssok.renewal.application.media.upload.shared.UploadRoomValidator;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.database.UploadFolderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("renewalIssueUploadUrlsService")
@RequiredArgsConstructor
public class IssueUploadUrlsService {
    private final UploadRoomValidator uploadRoomValidator;
    private final UploadFolderValidator uploadFolderValidator;
    private final UploadFileValidator uploadFileValidator;
    private final UploadFileRepository uploadFileRepository;
    private final UploadFolderRepository uploadFolderRepository;
    private final UploadUrlIssuer uploadUrlIssuer;

    @Transactional
    public IssueUploadUrlsResult issue(IssueUploadUrlsCommand command) {
        command.validate();
        uploadRoomValidator.validate(command.roomId(), command.uploaderId());
        List<Long> targetFolderIds = uploadFolderValidator.validate(command.roomId(), command.folderIds());
        ValidatedUploadFiles validatedFiles = uploadFileValidator.validate(command);
        List<StoredFile> storedUploadFiles = uploadFileRepository.savePendingUploads(validatedFiles.acceptedFiles());
        for (StoredFile storedUploadFile : storedUploadFiles) {
            uploadFolderRepository.addFileToFolders(storedUploadFile.getId(), targetFolderIds);
        }
        List<UploadUrl> issuedUploadUrls = storedUploadFiles.stream().map(uploadUrlIssuer::issue).toList();
        return new IssueUploadUrlsResult(issuedUploadUrls, validatedFiles.rejectedFiles());
    }
}
