package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.StoredFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 검증을 통과한 미디어만 받아 상태를 넘기고 저장한다.
// 스토리지 확인은 이 트랜잭션 밖에서 끝나 있어야 한다 — 네트워크 왕복 동안 DB 커넥션을 쥐고 있으면
// 등록 몇 건만으로 풀이 마르고 무관한 API 까지 함께 멈춘다.
@Component
@RequiredArgsConstructor
public class MediaRegistrar {

    private final FileRepository fileRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<MediaDetail> register(Long roomId, List<StoredFile> verified, String uploaderName) {
        if (verified.isEmpty()) {
            return List.of();
        }
        List<Long> mediaIds = verified.stream().map(StoredFile::getId).toList();
        Map<Long, List<Long>> folderIds = folderMediaRepository.findFolderIdsByMedia(mediaIds);

        List<MediaDetail> registered = new ArrayList<>();
        for (StoredFile file : verified) {
            file.startProcessing();
            StoredFile saved = fileRepository.save(file);
            registered.add(MediaDetail.of(saved, uploaderName,
                folderIds.getOrDefault(saved.getId(), List.of())));
        }
        registered.forEach(media -> eventPublisher.publishEvent(new MediaCreatedEvent(roomId, media)));
        return registered;
    }
}
