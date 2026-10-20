package com.sssok.renewal.application.media.upload.reissue;

import com.sssok.renewal.application.media.upload.shared.UploadUrl;
import com.sssok.renewal.application.media.upload.shared.UploadUrlIssuer;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.outgoing.config.UploadProperties;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("renewalReissueUploadUrlService")
@RequiredArgsConstructor
public class ReissueUploadUrlService {
    private final ReissueUploadValidator reissueUploadValidator;
    private final UploadFileRepository uploadFileRepository;
    private final UploadUrlIssuer uploadUrlIssuer;
    private final UploadProperties uploadProperties;

    @Transactional
    public ReissuedUploadUrl reissue(ReissueUploadUrlCommand command) {
        StoredFile file = reissueUploadValidator.validate(command);
        file.reissueUploadUrl(uploadProperties.maxRetryCount(), Instant.now());
        StoredFile reissuedFile = uploadFileRepository.save(file);
        UploadUrl uploadUrl = uploadUrlIssuer.issue(reissuedFile);
        return ReissuedUploadUrl.of(uploadUrl, reissuedFile.getRetryCount(), uploadProperties.maxRetryCount());
    }
}
