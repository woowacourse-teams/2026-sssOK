package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import org.springframework.stereotype.Component;

// TODO(#25): 파일 영속화(엔티티·테이블)가 아직 없어 지울 대상이 없다.
// StoredFile 저장이 생기면 roomId 로 찾아 지우도록 채운다. 그 전까지 정리 배치는 방만 지운다.
@Component
public class FileRepositoryAdapter implements FileRepository {

    @Override
    public List<StoredFile> findAllByRoomId(Long roomId) {
        return List.of();
    }

    @Override
    public void deleteAllByRoomId(Long roomId) {
    }
}
