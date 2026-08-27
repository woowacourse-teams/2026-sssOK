package com.sssok.application.media;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadRejectionReason;
import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.InvalidFileSizeException;
import com.sssok.domain.file.exception.UnsupportedMediaTypeException;
import com.sssok.infrastructure.config.UploadProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class IssueUploadUrlsService {

    private static final String PUT = "PUT";

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final FolderRepository folderRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final RoomPermissionPort roomPermissionPort;
    private final UploadProperties uploadProperties;

    @Transactional
    public IssueUploadUrlsResult issue(Long roomId, Long uploaderId,
                                       List<UploadFileCommand> files, List<Long> folderIds) {
        if (files == null || files.isEmpty()) {
            throw new InvalidUploadParamException("업로드할 파일이 없습니다");
        }
        if (!roomPermissionPort.canUpload(roomId, uploaderId)) {
            throw new UploadNotAllowedException();
        }
        List<Long> targetFolderIds = validateFolders(roomId, folderIds);

        Instant now = Instant.now();
        List<StoredFile> reserved = new ArrayList<>();
        List<String> reservedFileNames = new ArrayList<>();
        List<RejectedFile> rejected = new ArrayList<>();

        // 파일 하나가 걸러져도 나머지는 발급한다. 프론트가 실패한 것만 골라 다시 올릴 수 있어야 한다.
        for (UploadFileCommand file : files) {
            try {
                reserved.add(StoredFile.reserve(roomId, uploaderId, file.fileName(),
                    file.mimeType(), new FileSize(file.size()), now));
                reservedFileNames.add(file.fileName());
            } catch (UnsupportedMediaTypeException e) {
                rejected.add(RejectedFile.of(file.fileName(), UploadRejectionReason.UNSUPPORTED_MEDIA_TYPE));
            } catch (FileSizeExceededException e) {
                rejected.add(RejectedFile.of(file.fileName(), UploadRejectionReason.FILE_TOO_LARGE));
            } catch (InvalidFileSizeException | NullPointerException e) {
                rejected.add(RejectedFile.of(file.fileName(), UploadRejectionReason.INVALID_PARAM));
            }
        }

        List<StoredFile> saved = fileRepository.saveAll(reserved);
        attachToFolders(targetFolderIds, saved);

        return new IssueUploadUrlsResult(presign(saved, reservedFileNames), rejected);
    }

    // 없는 폴더가 하나라도 있으면 요청 전체를 막는다. 일부만 담기면 어디에 들어갔는지 알 수 없다.
    private List<Long> validateFolders(Long roomId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        // 다른 방 폴더에 담지 못하도록 소속까지 확인한다.
        List<Long> existing = folderRepository.findAllById(folderIds).stream()
            .filter(folder -> folder.belongsTo(roomId))
            .map(Folder::getId)
            .toList();
        List<Long> missing = folderIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new FolderNotFoundException(missing);
        }
        return existing;
    }

    private void attachToFolders(List<Long> folderIds, List<StoredFile> saved) {
        if (folderIds.isEmpty() || saved.isEmpty()) {
            return;
        }
        List<Long> mediaIds = saved.stream().map(StoredFile::getId).toList();
        folderIds.forEach(folderId -> folderMediaRepository.attachToFolder(folderId, mediaIds));
    }

    private List<UploadUrl> presign(List<StoredFile> saved, List<String> fileNames) {
        int expiresIn = (int) uploadProperties.presignedUrlTtl().toSeconds();
        List<UploadUrl> issued = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            StoredFile file = saved.get(i);
            String contentType = file.getMediaType().contentType();
            String url = fileStoragePort.presignPut(
                file.getStorageKey(), contentType, uploadProperties.presignedUrlTtl());
            issued.add(new UploadUrl(file.getId(), fileNames.get(i), url, PUT,
                Map.of("Content-Type", contentType), expiresIn));
        }
        return issued;
    }
}
