package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderRepository;
import org.springframework.stereotype.Component;

// TODO(#25): 폴더 영속화(엔티티·테이블)가 아직 없어 지울 대상이 없다.
@Component
public class FolderRepositoryAdapter implements FolderRepository {

    @Override
    public void deleteAllByRoomId(Long roomId) {
    }
}
